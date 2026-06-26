package com.rental.shortrental.integration.partner.messaging;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.application.InboundMessageDto;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;

import java.util.List;

public interface LivePartnerMessagingClient {
    ExternalPlatform platform();

    String providerName();

    List<String> capabilities();

    List<String> requiredCredentials();

    boolean hasLiveCredentials(ExternalIntegrationConfig config);

    List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config);

    boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content);
}
