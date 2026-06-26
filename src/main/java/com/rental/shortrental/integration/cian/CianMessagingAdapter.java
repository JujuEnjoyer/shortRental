package com.rental.shortrental.integration.cian;

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
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
public class CianMessagingAdapter implements ExternalMessagingPort {

    private static final Logger log = LoggerFactory.getLogger(CianMessagingAdapter.class);
    private static final int CHAT_PAGE_SIZE = 50;
    private static final int MESSAGE_PAGE_SIZE = 100;

    private final CianMessengerClient messengerClient;

    public CianMessagingAdapter(CianMessengerClient messengerClient) {
        this.messengerClient = messengerClient;
    }

    @Override
    public MessagingAdapterDescriptor descriptor() {
        return new MessagingAdapterDescriptor(
                "cian-open-api",
                "Cian Public API",
                IntegrationMode.OPEN_API,
                List.of(ExternalPlatform.CIAN),
                List.of("fetchChats", "fetchMessages", "sendReplies", "documentLinks", "polling"),
                List.of("authToken", "platformUserId"),
                false,
                "ACCESS KEY передается как Bearer token; platformUserId используется как optional employeeId."
        );
    }

    @Override
    public boolean supports(ExternalIntegrationConfig config) {
        return config.isEnabled()
                && config.getPlatform() == ExternalPlatform.CIAN
                && config.getMode() == IntegrationMode.OPEN_API
                && hasAccessKey(config);
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        List<InboundMessageDto> result = new ArrayList<>();
        if (!hasAccessKey(config)) {
            return result;
        }
        try {
            Integer employeeId = parseEmployeeId(config.getPlatformUserId());
            for (JsonNode chat : messengerClient.fetchChats(config.getAuthToken(), employeeId, 1, CHAT_PAGE_SIZE)) {
                long chatId = chat.path("chatId").asLong(0);
                if (chatId <= 0) {
                    continue;
                }
                for (JsonNode msg : messengerClient.fetchMessages(config.getAuthToken(), chatId, 1, MESSAGE_PAGE_SIZE, false)) {
                    if (!"in".equalsIgnoreCase(msg.path("direction").asString(""))) {
                        continue;
                    }
                    String text = CianJson.text(msg);
                    if (text.isBlank()) {
                        continue;
                    }
                    String messageId = msg.path("messageId").asString("");
                    String guestLabel = CianJson.userLabel(msg.get("user"));
                    if (guestLabel.isBlank()) {
                        guestLabel = "Гость Циан";
                    }
                    result.add(new InboundMessageDto(
                            config.getProperty().getId(),
                            ExternalPlatform.CIAN,
                            String.valueOf(chatId),
                            "cian:" + chatId + ":" + messageId,
                            guestLabel,
                            text,
                            CianJson.offsetDateTime(msg.path("createdAt").asString(""))
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Cian fetchInboundMessages failed for config id={}", config.getId(), e);
        }
        return result;
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        if (!hasAccessKey(config)) {
            return false;
        }
        try {
            long chatId = Long.parseLong(externalConversationId);
            return messengerClient.sendTextMessage(config.getAuthToken(), chatId, content);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasAccessKey(ExternalIntegrationConfig config) {
        String key = config.getAuthToken();
        return key != null && !key.isBlank();
    }

    public static Integer parseEmployeeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
