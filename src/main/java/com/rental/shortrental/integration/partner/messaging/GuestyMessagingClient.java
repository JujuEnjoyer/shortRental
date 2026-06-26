package com.rental.shortrental.integration.partner.messaging;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class GuestyMessagingClient extends AbstractLivePartnerMessagingClient {
    private static final String BASE_URL = "https://open-api.guesty.com/v1";

    public GuestyMessagingClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ExternalPlatform platform() {
        return ExternalPlatform.GUESTY;
    }

    @Override
    public String providerName() {
        return "Guesty Open API";
    }

    @Override
    public List<String> capabilities() {
        return List.of("fetchConversations", "fetchMessages", "sendReplies", "messageWebhooks");
    }

    @Override
    public List<String> requiredCredentials() {
        return List.of("authToken");
    }

    @Override
    public boolean hasLiveCredentials(ExternalIntegrationConfig config) {
        return hasToken(config);
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        List<InboundMessageDto> result = new ArrayList<>();
        if (!hasLiveCredentials(config)) {
            return result;
        }
        String base = endpoint(config, BASE_URL);
        try {
            JsonNode root = authorizedGetJson(join(base, "/communication/conversations?limit=50"), config);
            for (JsonNode thread : PartnerMessagingJson.arrayAt(root, "results", "data", "conversations", "items")) {
                String conversationId = conversationId(thread, "_id", "id", "conversationId");
                if (conversationId.isBlank()) {
                    continue;
                }
                JsonNode messagesRoot = authorizedGetJson(
                        join(base, "/communication/conversations/" + conversationId + "/messages?limit=50"),
                        config
                );
                for (JsonNode message : PartnerMessagingJson.arrayAt(messagesRoot, "results", "data", "messages", "items")) {
                    if (!PartnerMessagingJson.incoming(message, config.getPlatformUserId())) {
                        continue;
                    }
                    String text = PartnerMessagingJson.firstText(message, "body", "text", "message", "content");
                    if (text.isBlank()) {
                        continue;
                    }
                    result.add(new InboundMessageDto(
                            config.getProperty().getId(),
                            ExternalPlatform.GUESTY,
                            conversationId,
                            "guesty:" + conversationId + ":" + messageId(message),
                            guestName(thread, message),
                            text,
                            PartnerMessagingJson.dateTime(message, "createdAt", "created_at", "sentAt")
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Guesty fetchInboundMessages failed for config id={}", config.getId(), e);
        }
        return result;
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        if (!hasLiveCredentials(config)) {
            return false;
        }
        String base = endpoint(config, BASE_URL);
        return authorizedPostJson(
                join(base, "/communication/conversations/" + externalConversationId + "/send-message"),
                config,
                Map.of("body", content, "message", content)
        );
    }

    private static String messageId(JsonNode message) {
        String id = PartnerMessagingJson.firstText(message, "_id", "id", "messageId");
        return id.isBlank() ? String.valueOf(System.nanoTime()) : id;
    }

    private static String guestName(JsonNode thread, JsonNode message) {
        String name = PartnerMessagingJson.firstText(message, "guest.name", "sender.name", "from.name");
        if (!name.isBlank()) {
            return name;
        }
        name = PartnerMessagingJson.firstText(thread, "guest.name", "guest.fullName", "reservation.guest.fullName");
        return name.isBlank() ? "Гость Guesty" : name;
    }
}
