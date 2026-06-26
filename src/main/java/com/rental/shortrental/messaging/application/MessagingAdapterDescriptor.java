package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.entity.IntegrationMode;

import java.util.List;

public record MessagingAdapterDescriptor(
        String code,
        String title,
        IntegrationMode mode,
        List<ExternalPlatform> platforms,
        List<String> capabilities,
        List<String> requiredFields,
        boolean demoOnly,
        String note
) {
}
