package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;

import java.time.OffsetDateTime;

public record InboundMessageDto(
        Long propertyId,
        ExternalPlatform platform,
        String externalConversationId,
        String externalMessageId,
        String guestDisplayName,
        String content,
        OffsetDateTime createdAt
) {
}
