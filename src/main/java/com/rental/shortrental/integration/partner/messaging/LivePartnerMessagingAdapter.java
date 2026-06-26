package com.rental.shortrental.integration.partner.messaging;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.ExternalMessagingPort;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.application.MessagingAdapterDescriptor;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(50)
public class LivePartnerMessagingAdapter implements ExternalMessagingPort {
    private final LivePartnerMessagingClientRegistry registry;

    public LivePartnerMessagingAdapter(LivePartnerMessagingClientRegistry registry) {
        this.registry = registry;
    }

    @Override
    public MessagingAdapterDescriptor descriptor() {
        return new MessagingAdapterDescriptor(
                "live-partner-messaging-api",
                "Unified live messaging APIs",
                IntegrationMode.OPEN_API,
                registry.all().stream().map(LivePartnerMessagingClient::platform).toList(),
                List.of("fetchMessages", "sendReplies", "documentLinks", "polling", "perPlatformAdapters"),
                List.of("authToken", "optional outboundEndpoint for custom API base"),
                false,
                "Общий порт сообщений делегирует работу отдельному клиенту Hostaway, Channex, Booking.com или Guesty."
        );
    }

    @Override
    public boolean supports(ExternalIntegrationConfig config) {
        return config.isEnabled()
                && config.getMode() == IntegrationMode.OPEN_API
                && registry.find(config.getPlatform())
                .map(client -> client.hasLiveCredentials(config))
                .orElse(false);
    }

    @Override
    public List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config) {
        return registry.find(config.getPlatform())
                .map(client -> client.fetchInboundMessages(config))
                .orElseGet(List::of);
    }

    @Override
    public boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content) {
        return registry.find(config.getPlatform())
                .map(client -> client.sendReply(config, externalConversationId, content))
                .orElse(false);
    }
}
