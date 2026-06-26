package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.integration.cian.CianJson;
import com.rental.shortrental.integration.cian.CianMessagingAdapter;
import com.rental.shortrental.integration.cian.CianMessengerClient;
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
public class CianLandlordInboxService {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_CHAT_PAGES = 20;
    private static final int MAX_MESSAGE_PAGES = 10;

    private final ExternalIntegrationConfigRepository integrationRepository;
    private final CianMessengerClient messengerClient;

    public CianLandlordInboxService(
            ExternalIntegrationConfigRepository integrationRepository,
            CianMessengerClient messengerClient
    ) {
        this.integrationRepository = integrationRepository;
        this.messengerClient = messengerClient;
    }

    public List<AvitoChatListItem> listAllChats(Long ownerId) {
        List<AvitoChatListItem> out = new ArrayList<>();
        for (ExternalIntegrationConfig config : integrationRepository.findByPropertyUserId(ownerId)) {
            if (!isCianOpenApiWithKey(config)) {
                continue;
            }
            Integer employeeId = CianMessagingAdapter.parseEmployeeId(config.getPlatformUserId());
            List<JsonNode> chats = fetchAllChatPages(config.getAuthToken(), employeeId);
            for (JsonNode chat : chats) {
                long chatId = chat.path("chatId").asLong(0);
                if (chatId <= 0) {
                    continue;
                }
                JsonNode lastMessage = chat.get("lastMessage");
                String preview = lastMessage == null ? "" : CianJson.text(lastMessage);
                String counterparty = lastMessage == null ? "" : CianJson.userLabel(lastMessage.get("user"));
                if (counterparty.isBlank()) {
                    counterparty = "Гость Циан";
                }
                JsonNode offer = chat.get("offer");
                String title = "Циан чат " + chatId;
                if (offer != null && offer.hasNonNull("id")) {
                    title = "Объявление #" + offer.path("id").asLong();
                }
                long updated = CianJson.epochSeconds(chat.path("updatedAt").asString(""));
                out.add(new AvitoChatListItem(
                        config.getId(),
                        config.getProperty().getId(),
                        config.getProperty().getName(),
                        String.valueOf(chatId),
                        title,
                        preview,
                        updated,
                        counterparty,
                        ExternalPlatform.CIAN
                ));
            }
        }
        out.sort(Comparator.comparingLong(AvitoChatListItem::updatedAt).reversed());
        return out;
    }

    public List<AvitoThreadMessageDto> listThreadMessages(Long ownerId, long propertyId, String chatIdRaw) {
        long chatId;
        try {
            chatId = Long.parseLong(chatIdRaw);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chatId должен быть числом Циан");
        }
        ExternalIntegrationConfig config = requireCianConfig(ownerId, propertyId);
        List<JsonNode> raw = fetchAllMessagePages(config.getAuthToken(), chatId);
        List<AvitoThreadMessageDto> messages = new ArrayList<>();
        for (JsonNode msg : raw) {
            String id = msg.path("messageId").asString("");
            String direction = msg.path("direction").asString("");
            String text = CianJson.text(msg);
            long authorId = msg.path("userId").asLong(0);
            String authorName = CianJson.userLabel(msg.get("user"));
            long created = CianJson.epochSeconds(msg.path("createdAt").asString(""));
            messages.add(new AvitoThreadMessageDto(id, direction, "text", text, authorId, authorName, created));
        }
        messages.sort(Comparator.comparingLong(AvitoThreadMessageDto::created));
        return messages;
    }

    private ExternalIntegrationConfig requireCianConfig(Long ownerId, long propertyId) {
        return integrationRepository.findByPropertyUserId(ownerId).stream()
                .filter(c -> c.getProperty().getId().equals(propertyId))
                .filter(c -> c.getPlatform() == ExternalPlatform.CIAN)
                .filter(c -> c.getMode() == IntegrationMode.OPEN_API)
                .filter(this::isCianOpenApiWithKey)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Для этого объекта не найден канал CIAN OPEN_API с ACCESS KEY"
                ));
    }

    private boolean isCianOpenApiWithKey(ExternalIntegrationConfig c) {
        String key = c.getAuthToken();
        return c.isEnabled()
                && c.getPlatform() == ExternalPlatform.CIAN
                && c.getMode() == IntegrationMode.OPEN_API
                && key != null && !key.isBlank();
    }

    private List<JsonNode> fetchAllChatPages(String accessKey, Integer employeeId) {
        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= MAX_CHAT_PAGES; page++) {
            List<JsonNode> batch = messengerClient.fetchChats(accessKey, employeeId, page, PAGE_SIZE);
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    private List<JsonNode> fetchAllMessagePages(String accessKey, long chatId) {
        List<JsonNode> all = new ArrayList<>();
        for (int page = 1; page <= MAX_MESSAGE_PAGES; page++) {
            List<JsonNode> batch = messengerClient.fetchMessages(accessKey, chatId, page, PAGE_SIZE, false);
            all.addAll(batch);
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }
}
