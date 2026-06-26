package com.rental.shortrental.integration.partner.messaging;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ChannexMessagingClient extends AbstractLivePartnerMessagingClient {
    private static final String BASE_URL = "https://app.channex.io/api/v1";

    public ChannexMessagingClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ExternalPlatform platform() {
        return ExternalPlatform.CHANNEX;
    }

    @Override
    public String providerName() {
        return "Channex API";
    }

    @Override
    public List<String> capabilities() {
        return List.of("channelMessages", "bookingComMessages", "expediaMessages", "airbnbMessages", "sendReplies");
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
    protected void applyAuth(HttpHeaders headers, ExternalIntegrationConfig config) {
        if (config.getAuthToken() != null && !config.getAuthToken().isBlank()) {
            headers.set("user-api-key", config.getAuthToken().trim());
        }
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        List<InboundMessageDto> result = new ArrayList<>();
        if (!hasLiveCredentials(config)) {
            return result;
        }
        String base = endpoint(config, BASE_URL);
        try {
            JsonNode root = authorizedGetJson(join(base, "/message_threads?limit=50"), config);
            for (JsonNode thread : PartnerMessagingJson.arrayAt(root, "data", "message_threads", "items")) {
                String threadId = conversationId(thread, "id", "attributes.id", "attributes.message_thread_id");
                if (threadId.isBlank()) {
                    continue;
                }
                JsonNode messagesRoot = authorizedGetJson(
                        join(base, "/message_threads/" + threadId + "/messages"),
                        config
                );
                for (JsonNode message : PartnerMessagingJson.arrayAt(messagesRoot, "data", "messages", "items")) {
                    if (!PartnerMessagingJson.incoming(message, config.getPlatformUserId())) {
                        continue;
                    }
                    String text = PartnerMessagingJson.firstText(message, "attributes.message", "attributes.text", "message", "text");
                    if (text.isBlank()) {
                        continue;
                    }
                    result.add(new InboundMessageDto(
                            config.getProperty().getId(),
                            ExternalPlatform.CHANNEX,
                            threadId,
                            "channex:" + threadId + ":" + messageId(message),
                            guestName(thread, message),
                            text,
                            PartnerMessagingJson.dateTime(message, "attributes.inserted_at", "attributes.created_at", "created_at")
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Channex fetchInboundMessages failed for config id={}", config.getId(), e);
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
                join(base, "/message_threads/" + externalConversationId + "/messages"),
                config,
                Map.of(
                        "data", Map.of(
                                "type", "message",
                                "attributes", Map.of(
                                        "message", content
                                )
                        )
                )
        );
    }

    private static String messageId(JsonNode message) {
        String id = PartnerMessagingJson.firstText(message, "id", "attributes.id", "attributes.message_id");
        return id.isBlank() ? String.valueOf(System.nanoTime()) : id;
    }

    private static String guestName(JsonNode thread, JsonNode message) {
        String name = PartnerMessagingJson.firstText(
                message,
                "attributes.sender_name",
                "attributes.user_name",
                "sender.name"
        );
        if (!name.isBlank()) {
            return name;
        }
        name = PartnerMessagingJson.firstText(thread, "attributes.guest_name", "attributes.customer_name", "guest.name");
        return name.isBlank() ? "Гость Channex" : name;
    }
}
