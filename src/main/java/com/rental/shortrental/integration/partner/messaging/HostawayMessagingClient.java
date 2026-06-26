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
public class HostawayMessagingClient extends AbstractLivePartnerMessagingClient {
    private static final String BASE_URL = "https://api.hostaway.com/v1";

    public HostawayMessagingClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ExternalPlatform platform() {
        return ExternalPlatform.HOSTAWAY;
    }

    @Override
    public String providerName() {
        return "Hostaway API";
    }

    @Override
    public List<String> capabilities() {
        return List.of("fetchConversations", "fetchMessages", "sendReplies", "documentLinks", "polling");
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
            JsonNode root = authorizedGetJson(join(base, "/conversations?limit=50"), config);
            for (JsonNode thread : PartnerMessagingJson.arrayAt(root, "result", "data", "conversations", "items")) {
                String conversationId = conversationId(thread, "id", "conversationId", "uid");
                if (conversationId.isBlank()) {
                    continue;
                }
                JsonNode messagesRoot = authorizedGetJson(join(base, "/conversations/" + conversationId + "/messages?limit=50"), config);
                for (JsonNode message : PartnerMessagingJson.arrayAt(messagesRoot, "result", "data", "messages", "items")) {
                    if (!PartnerMessagingJson.incoming(message, config.getPlatformUserId())) {
                        continue;
                    }
                    String text = PartnerMessagingJson.firstText(message, "body", "message", "text", "content", "messageBody");
                    if (text.isBlank()) {
                        continue;
                    }
                    result.add(new InboundMessageDto(
                            config.getProperty().getId(),
                            ExternalPlatform.HOSTAWAY,
                            conversationId,
                            "hostaway:" + conversationId + ":" + messageId(message),
                            guestName(thread, message),
                            text,
                            PartnerMessagingJson.dateTime(message, "createdAt", "created_at", "date", "timestamp")
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Hostaway fetchInboundMessages failed for config id={}", config.getId(), e);
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
                join(base, "/conversations/" + externalConversationId + "/messages"),
                config,
                Map.of("body", content, "message", content)
        );
    }

    private static String messageId(JsonNode message) {
        String id = PartnerMessagingJson.firstText(message, "id", "messageId", "uid");
        return id.isBlank() ? String.valueOf(System.nanoTime()) : id;
    }

    private static String guestName(JsonNode thread, JsonNode message) {
        String name = PartnerMessagingJson.firstText(
                message,
                "guestName",
                "guest.name",
                "sender.name",
                "author.name"
        );
        if (!name.isBlank()) {
            return name;
        }
        name = PartnerMessagingJson.firstText(thread, "guestName", "guest.name", "customer.name");
        return name.isBlank() ? "Гость Hostaway" : name;
    }
}
