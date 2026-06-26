package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;

import java.util.List;
import java.util.Map;

public record PartnerApiCheckResult(
        boolean success,
        ExternalPlatform platform,
        String provider,
        String mode,
        String message,
        List<String> capabilities,
        List<String> limitations,
        Map<String, Object> details
) {
    public static PartnerApiCheckResult mock(ExternalPlatform platform, String provider, List<String> capabilities, List<String> limitations) {
        return new PartnerApiCheckResult(
                true,
                platform,
                provider,
                "MOCK",
                "Mock-режим включен: канал можно показать на защите без реального токена.",
                capabilities,
                limitations,
                Map.of()
        );
    }
}
