package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.ExternalMessagingPort;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.application.MessagingAdapterDescriptor;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class EmailRelayMessagingAdapter implements ExternalMessagingPort {
    @Override
    public MessagingAdapterDescriptor descriptor() {
        return new MessagingAdapterDescriptor(
                "email-relay",
                "Email relay / hidden reply address",
                IntegrationMode.EMAIL_RELAY,
                List.of(
                        ExternalPlatform.BOOKING,
                        ExternalPlatform.AIRBNB,
                        ExternalPlatform.HOSTAWAY,
                        ExternalPlatform.CHANNEX,
                        ExternalPlatform.GUESTY,
                        ExternalPlatform.OSTROVOK,
                        ExternalPlatform.YANDEX_TRAVEL,
                        ExternalPlatform.SUTOCHNO,
                        ExternalPlatform.OTHER
                ),
                List.of("fetchMessages", "sendReplies", "documentLinks", "polling"),
                List.of("inboundEndpoint", "outboundEndpoint", "authToken"),
                true,
                "Fallback-канал для площадок без открытого API: IMAP/SMTP или webhook почтового шлюза."
        );
    }

    @Override
    public boolean supports(ExternalIntegrationConfig config) {
        return config.isEnabled() && config.getMode() == IntegrationMode.EMAIL_RELAY;
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        // TODO: parse mailbox/webhook and map hidden relay address to conversation id.
        return List.of(
                new InboundMessageDto(
                        config.getProperty().getId(),
                        config.getPlatform(),
                        "email-conv-" + config.getId(),
                        "email-demo-in-" + config.getId(),
                        "Guest via relay email",
                        "Добрый день, можно ли заехать раньше?",
                        OffsetDateTime.now()
                )
        );
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        // TODO: send email to relay address belonging to externalConversationId.
        return true;
    }
}
