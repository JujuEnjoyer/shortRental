package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.integration.avito.AvitoJson;
import com.rental.shortrental.integration.avito.AvitoMessengerClient;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AvitoLandlordInboxService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_CHAT_PAGES = 100;
    /**
     * Loading hundreds of pages sequentially makes the UI feel "stuck"; duplicate-page detection stops broken offsets early.
     */
    private static final int MAX_MESSAGE_PAGES = 12;

    private final ExternalIntegrationConfigRepository integrationRepository;
    private final AvitoMessengerClient messengerClient;

    public AvitoLandlordInboxService(
            ExternalIntegrationConfigRepository integrationRepository,
            AvitoMessengerClient messengerClient
    ) {
        this.integrationRepository = integrationRepository;
        this.messengerClient = messengerClient;
    }

    public List<AvitoChatListItem> listAllChats(Long ownerId) {
        List<AvitoChatListItem> out = new ArrayList<>();
        for (ExternalIntegrationConfig config : integrationRepository.findByPropertyUserId(ownerId)) {
            if (!config.isEnabled() || !isAvitoOpenApiWithCreds(config)) {
                continue;
            }
            long avitoUserId;
            try {
                avitoUserId = Long.parseLong(config.getPlatformUserId().trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String itemFilter = config.getInboundEndpoint();
            List<JsonNode> chats = fetchAllChatPages(
                    config.getClientId(),
                    config.getClientSecret(),
                    avitoUserId,
                    itemFilter
            );
            for (JsonNode chat : chats) {
                String chatId = chat.path("id").asString(null);
                if (chatId == null || chatId.isBlank()) {
                    continue;
                }
                String title = AvitoJson.chatTitle(chat);
                if (title == null || title.isBlank()) {
                    title = "Чат " + chatId;
                }
                String preview = AvitoJson.chatPreview(chat);
                long updated = AvitoJson.chatUpdated(chat);
                String counterparty = AvitoJson.counterpartyName(chat, avitoUserId);
                out.add(new AvitoChatListItem(
                        config.getId(),
                        config.getProperty().getId(),
                        config.getProperty().getName(),
                        chatId,
                        title,
                        preview == null ? "" : preview,
                        updated,
                        counterparty == null ? "" : counterparty,
                        ExternalPlatform.AVITO
                ));
            }
        }
        out.sort(Comparator.comparingLong(AvitoChatListItem::updatedAt).reversed());
        return out;
    }

    public List<AvitoThreadMessageDto> listThreadMessages(Long ownerId, long propertyId, String chatId) {
        if (chatId == null || chatId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId required");
        }
        ExternalIntegrationConfig config = requireAvitoConfig(ownerId, propertyId);
        long avitoUserId;
        try {
            avitoUserId = Long.parseLong(config.getPlatformUserId().trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid platformUserId");
        }
        List<JsonNode> raw = fetchAllMessagePages(
                config.getClientId(),
                config.getClientSecret(),
                avitoUserId,
                chatId.trim()
        );
        List<AvitoThreadMessageDto> messages = new ArrayList<>();
        for (JsonNode msg : raw) {
            String id = msg.path("id").asString("");
            String direction = msg.path("direction").asString("");
            String type = msg.path("type").asString("");
            String text = AvitoJson.messageText(msg);
            if (text == null) {
                text = "";
            }
            long authorId = msg.path("author_id").asLong(0);
            String authorName = AvitoJson.messageAuthorLabel(msg);
            long created = msg.path("created").asLong(0);
            messages.add(new AvitoThreadMessageDto(id, direction, type, text, authorId, authorName, created));
        }
        messages.sort(Comparator.comparingLong(AvitoThreadMessageDto::created));
        return messages;
    }

    private ExternalIntegrationConfig requireAvitoConfig(Long ownerId, long propertyId) {
        return integrationRepository.findByPropertyUserId(ownerId).stream()
                .filter(c -> c.getProperty().getId().equals(propertyId))
                .filter(c -> c.getPlatform() == ExternalPlatform.AVITO)
                .filter(c -> c.getMode() == IntegrationMode.OPEN_API)
                .filter(ExternalIntegrationConfig::isEnabled)
                .filter(this::isAvitoOpenApiWithCreds)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Для этого объекта не найден канал Avito OPEN_API с ключами"
                ));
    }

    private boolean isAvitoOpenApiWithCreds(ExternalIntegrationConfig c) {
        String clientId = c.getClientId();
        String secret = c.getClientSecret();
        String uid = c.getPlatformUserId();
        return clientId != null && !clientId.isBlank()
                && secret != null && !secret.isBlank()
                && uid != null && !uid.isBlank();
    }

    private List<JsonNode> fetchAllChatPages(
            String clientId,
            String clientSecret,
            long avitoUserId,
            String itemIdsCsv
    ) {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_CHAT_PAGES; page++) {
            List<JsonNode> batch = messengerClient.fetchChats(
                    clientId, clientSecret, avitoUserId, PAGE_SIZE, offset, itemIdsCsv
            );
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return all;
    }

    private List<JsonNode> fetchAllMessagePages(
            String clientId,
            String clientSecret,
            long avitoUserId,
            String chatId
    ) {
        List<JsonNode> all = new ArrayList<>();
        int offset = 0;
        String previousFirstId = null;
        for (int page = 0; page < MAX_MESSAGE_PAGES; page++) {
            List<JsonNode> batch = messengerClient.fetchMessages(
                    clientId, clientSecret, avitoUserId, chatId, PAGE_SIZE, offset
            );
            if (batch.isEmpty()) {
                break;
            }
            String firstId = batch.getFirst().path("id").asString("");
            if (firstId.equals(previousFirstId)) {
                break;
            }
            previousFirstId = firstId;
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return all;
    }
}
