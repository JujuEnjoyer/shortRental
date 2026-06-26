package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;

public record ReplyMessageRequest(
        Long propertyId,
        ExternalPlatform platform,
        String externalConversationId,
        String guestDisplayName,
        String content,
        Long guestId,
        Long documentTemplateId
) {
}
