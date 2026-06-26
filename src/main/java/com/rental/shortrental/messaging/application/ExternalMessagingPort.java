package com.rental.shortrental.messaging.application;

import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;

import java.util.List;

public interface ExternalMessagingPort {
    MessagingAdapterDescriptor descriptor();

    boolean supports(ExternalIntegrationConfig config);

    List<InboundMessageDto> fetchInboundMessages(ExternalIntegrationConfig config);

    boolean sendReply(ExternalIntegrationConfig config, String externalConversationId, String content);
}
