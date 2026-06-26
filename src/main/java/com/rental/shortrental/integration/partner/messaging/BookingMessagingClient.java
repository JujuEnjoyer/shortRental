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
public class BookingMessagingClient extends AbstractLivePartnerMessagingClient {
    private static final String BASE_URL = "https://demandapi.booking.com/3.1";

    public BookingMessagingClient(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public ExternalPlatform platform() {
        return ExternalPlatform.BOOKING;
    }

    @Override
    public String providerName() {
        return "Booking.com Messaging API";
    }

    @Override
    public List<String> capabilities() {
        return List.of("fetchLatestMessages", "confirmLatestMessages", "sendReplies", "partnerAccessRequired");
    }

    @Override
    public List<String> requiredCredentials() {
        return List.of("authToken", "platformUserId as X-Affiliate-Id");
    }

    @Override
    public boolean hasLiveCredentials(ExternalIntegrationConfig config) {
        return hasToken(config);
    }

    @Override
    protected void applyAuth(HttpHeaders headers, ExternalIntegrationConfig config) {
        if (config.getAuthToken() != null && !config.getAuthToken().isBlank()) {
            headers.setBearerAuth(config.getAuthToken().trim());
        }
        if (config.getPlatformUserId() != null && !config.getPlatformUserId().isBlank()) {
            headers.set("X-Affiliate-Id", config.getPlatformUserId().trim());
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
            JsonNode root = authorizedPostJsonForResponse(join(base, "/messages/latest"), config, Map.of());
            List<String> receivedMessageIds = new ArrayList<>();
            for (JsonNode message : PartnerMessagingJson.arrayAt(root, "data.messages", "messages", "result.messages", "items")) {
                if (!PartnerMessagingJson.incoming(message, config.getPlatformUserId())) {
                    continue;
                }
                String text = PartnerMessagingJson.firstText(message, "message", "text", "body", "content", "content.text");
                if (text.isBlank()) {
                    continue;
                }
                String conversationId = conversationId(message, "conversation", "conversation_id", "conversationId", "conversation.id", "thread_id", "reservation_id");
                if (conversationId.isBlank()) {
                    continue;
                }
                String accommodationId = conversationId(message, "accommodation", "accommodation_id", "accommodation.id", "property_id");
                String threadId = accommodationId.isBlank()
                        ? conversationId
                        : conversationId + "|" + accommodationId;
                String messageId = messageId(message);
                receivedMessageIds.add(messageId);
                result.add(new InboundMessageDto(
                        config.getProperty().getId(),
                        ExternalPlatform.BOOKING,
                        threadId,
                        "booking:" + threadId + ":" + messageId,
                        guestName(message),
                        text,
                        PartnerMessagingJson.dateTime(message, "created_at", "createdAt", "timestamp")
                ));
            }
            confirmLatestMessages(base, config, receivedMessageIds);
        } catch (Exception e) {
            log.warn("Booking.com fetchInboundMessages failed for config id={}", config.getId(), e);
        }
        return result;
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        if (!hasLiveCredentials(config)) {
            return false;
        }
        String base = endpoint(config, BASE_URL);
        String[] parts = externalConversationId.split("\\|", 2);
        String conversationId = parts[0];
        String accommodationId = parts.length > 1 ? parts[1] : config.getInboundEndpoint();
        if (accommodationId == null || accommodationId.isBlank() || accommodationId.startsWith("http")) {
            log.warn("Booking.com send requires accommodation id. Put it in inboundEndpoint or receive it from messages/latest.");
            return false;
        }
        return authorizedPostJson(
                join(base, "/messages/send"),
                config,
                Map.of(
                        "conversation", conversationId,
                        "accommodation", accommodationId,
                        "content", content
                )
        );
    }

    private static String messageId(JsonNode message) {
        String id = PartnerMessagingJson.firstText(message, "id", "message_id", "messageId");
        return id.isBlank() ? String.valueOf(System.nanoTime()) : id;
    }

    private static String guestName(JsonNode message) {
        String name = PartnerMessagingJson.firstText(message, "guest_name", "guest.name", "sender.name");
        return name.isBlank() ? "Гость Booking.com" : name;
    }

    private void confirmLatestMessages(String base, ExternalIntegrationConfig config, List<String> messageIds) {
        List<String> clean = messageIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (!clean.isEmpty()) {
            authorizedPostJson(join(base, "/messages/latest/confirm"), config, Map.of("messages", clean));
        }
    }
}
