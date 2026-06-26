package com.rental.shortrental.integration.avito;

import tools.jackson.databind.JsonNode;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.ExternalMessagingPort;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.application.MessagingAdapterDescriptor;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Avito Messenger integration using per-user OAuth credentials.
 * Each user provides their own clientId/clientSecret from Avito professional cabinet.
 */
@Component
@Order(0)
public class AvitoMessagingAdapter implements ExternalMessagingPort {

    private static final Logger log = LoggerFactory.getLogger(AvitoMessagingAdapter.class);

    private static final int CHAT_LIMIT = 30;
    private static final int MESSAGE_LIMIT = 50;

    private final AvitoProperties avitoProperties;
    private final AvitoMessengerClient messengerClient;

    public AvitoMessagingAdapter(AvitoProperties avitoProperties, AvitoMessengerClient messengerClient) {
        this.avitoProperties = avitoProperties;
        this.messengerClient = messengerClient;
    }

    @Override
    public MessagingAdapterDescriptor descriptor() {
        return new MessagingAdapterDescriptor(
                "avito-open-api",
                "Avito Messenger API",
                IntegrationMode.OPEN_API,
                List.of(ExternalPlatform.AVITO),
                List.of("fetchChats", "fetchMessages", "sendReplies", "documentLinks", "polling"),
                List.of("clientId", "clientSecret", "platformUserId"),
                false,
                "Прямой API-адаптер. Для новой похожей площадки добавляется отдельный порт с тем же контрактом."
        );
    }

    @Override
    public boolean supports(ExternalIntegrationConfig config) {
        if (!avitoProperties.isEnabled()) {
            return false;
        }
        if (config.getPlatform() != ExternalPlatform.AVITO || config.getMode() != IntegrationMode.OPEN_API) {
            return false;
        }
        if (!config.isEnabled()) {
            return false;
        }
        boolean hasCreds = hasCredentials(config);
        log.debug("Avito adapter supports check: id={}, clientId={}, hasSecret={}, userId={}, result={}",
                config.getId(),
                config.getClientId() != null ? "present" : "null",
                config.getClientSecret() != null ? "present" : "null",
                config.getPlatformUserId(),
                hasCreds);
        return hasCreds;
    }

    private boolean hasCredentials(ExternalIntegrationConfig config) {
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();
        String userId = config.getPlatformUserId();
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && userId != null && !userId.isBlank();
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        List<InboundMessageDto> result = new ArrayList<>();
        if (!hasCredentials(config)) {
            return result;
        }

        long avitoUserId;
        try {
            avitoUserId = Long.parseLong(config.getPlatformUserId().trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid Avito platformUserId: {}", config.getPlatformUserId());
            return result;
        }

        String itemFilter = config.getInboundEndpoint();
        try {
            List<JsonNode> chats = messengerClient.fetchChats(
                    config.getClientId(), config.getClientSecret(),
                    avitoUserId, CHAT_LIMIT, 0, itemFilter);
            for (JsonNode chat : chats) {
                String chatId = chat.path("id").asString(null);
                if (chatId == null || chatId.isBlank()) {
                    continue;
                }
                List<JsonNode> messages = messengerClient.fetchMessages(
                        config.getClientId(), config.getClientSecret(),
                        avitoUserId, chatId, MESSAGE_LIMIT, 0);
                for (JsonNode msg : messages) {
                    if (!"in".equalsIgnoreCase(msg.path("direction").asString())) {
                        continue;
                    }
                    if ("system".equalsIgnoreCase(msg.path("type").asString(""))) {
                        continue;
                    }
                    String text = extractTextContent(msg);
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String messageId = msg.path("id").asString("");
                    String dedupeId = "avito:" + chatId + ":" + messageId;
                    long created = msg.path("created").asLong(0);
                    OffsetDateTime createdAt = created > 0
                            ? OffsetDateTime.ofInstant(Instant.ofEpochSecond(created), ZoneOffset.UTC)
                            : OffsetDateTime.now();
                    long authorId = msg.path("author_id").asLong(0);
                    String guestLabel = AvitoJson.messageAuthorLabel(msg);
                    result.add(new InboundMessageDto(
                            config.getProperty().getId(),
                            ExternalPlatform.AVITO,
                            chatId,
                            dedupeId,
                            guestLabel,
                            text,
                            createdAt
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Avito fetchInboundMessages failed for config id={}", config.getId(), e);
        }
        return result;
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        if (!hasCredentials(config)) {
            return false;
        }
        long avitoUserId;
        try {
            avitoUserId = Long.parseLong(config.getPlatformUserId().trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return messengerClient.sendTextMessage(
                config.getClientId(), config.getClientSecret(),
                avitoUserId, externalConversationId, content);
    }

    private static String extractTextContent(JsonNode msg) {
        JsonNode c = msg.get("content");
        if (c == null || c.isNull()) {
            return null;
        }
        if (c.hasNonNull("text")) {
            return c.get("text").asString();
        }
        return null;
    }
}
