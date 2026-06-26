package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;

/**
 * One Avito chat row for the landlord inbox (loaded live from Messenger API).
 */
public record AvitoChatListItem(
        long integrationId,
        long propertyId,
        String propertyName,
        String chatId,
        String title,
        String preview,
        long updatedAt,
        String counterpartyName,
        ExternalPlatform platform
) {
}
