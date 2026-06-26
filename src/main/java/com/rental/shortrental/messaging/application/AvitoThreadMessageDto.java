package com.rental.shortrental.messaging.application;

/**
 * One message in an Avito chat thread (from Messenger API).
 */
public record AvitoThreadMessageDto(
        String id,
        String direction,
        String type,
        String text,
        long authorId,
        String authorName,
        long created
) {
}
