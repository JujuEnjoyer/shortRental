package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.ExternalMessagingPort;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.application.MessagingAdapterDescriptor;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Component
@Order(100)
public class ApiMessagingAdapter implements ExternalMessagingPort {
    private static final Set<ExternalPlatform> PARTNER_API_PLATFORMS = Set.of(
            ExternalPlatform.SUTOCHNO,
            ExternalPlatform.YANDEX_TRAVEL,
            ExternalPlatform.OSTROVOK
    );

    @Override
    public MessagingAdapterDescriptor descriptor() {
        return new MessagingAdapterDescriptor(
                "partner-platform-open-api",
                "Partner platform OPEN_API bridge",
                IntegrationMode.OPEN_API,
                List.of(ExternalPlatform.SUTOCHNO, ExternalPlatform.YANDEX_TRAVEL, ExternalPlatform.OSTROVOK),
                List.of("verifyCredentials", "mockMessages", "documentLinks", "polling", "emailRelayFallback"),
                List.of("authToken/clientId/clientSecret when live API access exists"),
                true,
                "У этих площадок публичные API в основном про партнёрские данные/бронирования, а не чат; для защиты используется mock-чат, для реальной связи — EMAIL_RELAY или отдельный партнёрский endpoint."
        );
    }

    @Override
    public boolean supports(ExternalIntegrationConfig config) {
        return config.isEnabled()
                && config.getMode() == IntegrationMode.OPEN_API
                && PARTNER_API_PLATFORMS.contains(config.getPlatform());
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        return List.of(
                new InboundMessageDto(
                        config.getProperty().getId(),
                        config.getPlatform(),
                        conversationId(config),
                        messageId(config),
                        guestLabel(config),
                        demoMessage(config),
                        OffsetDateTime.now()
                )
        );
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        return isMockConfig(config);
    }

    private static String conversationId(ExternalIntegrationConfig config) {
        return config.getPlatform().name().toLowerCase() + "-demo-conv-" + config.getId();
    }

    private static String messageId(ExternalIntegrationConfig config) {
        return config.getPlatform().name().toLowerCase() + ":demo-in:" + config.getId();
    }

    private static String guestLabel(ExternalIntegrationConfig config) {
        return switch (config.getPlatform()) {
            case SUTOCHNO -> "Гость Суточно.ру";
            case YANDEX_TRAVEL -> "Гость Яндекс Путешествий";
            case OSTROVOK -> "Гость Островка";
            default -> "Гость площадки";
        };
    }

    private static String demoMessage(ExternalIntegrationConfig config) {
        if (isMockConfig(config)) {
            return switch (config.getPlatform()) {
                case SUTOCHNO -> "Здравствуйте, квартира свободна на выходные? Это mock-диалог Суточно.ру для демонстрации.";
                case YANDEX_TRAVEL -> "Добрый день! Можно ли ранний заезд? Это mock-диалог Яндекс Путешествий для демонстрации.";
                case OSTROVOK -> "Здравствуйте, интересует поздний выезд. Это mock-диалог Островка для демонстрации.";
                default -> "Тестовое сообщение гостя через partner API bridge.";
            };
        }
        return "API-ключ настроен. В публичной документации этой площадки нет чат-методов; для реальных ответов подключите EMAIL_RELAY или отдельный партнёрский endpoint.";
    }

    private static boolean isMockConfig(ExternalIntegrationConfig config) {
        return isBlank(config.getAuthToken())
                && isBlank(config.getClientId())
                && isBlank(config.getClientSecret())
                || startsWithMock(config.getAuthToken())
                || containsDemo(config.getInboundEndpoint())
                || containsDemo(config.getOutboundEndpoint());
    }

    private static boolean startsWithMock(String value) {
        return value != null && value.trim().toLowerCase().startsWith("mock");
    }

    private static boolean containsDemo(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("mock") || lower.contains("demo");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
