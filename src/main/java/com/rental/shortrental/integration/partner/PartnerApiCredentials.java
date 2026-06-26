package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;

public record PartnerApiCredentials(
        ExternalPlatform platform,
        String authToken,
        String clientId,
        String clientSecret,
        String platformUserId,
        String inboundEndpoint,
        String outboundEndpoint
) {
    public boolean mockRequested() {
        return startsWithMock(authToken)
                || startsWithMock(clientId)
                || startsWithMock(clientSecret)
                || containsDemo(inboundEndpoint)
                || containsDemo(outboundEndpoint);
    }

    public boolean hasToken() {
        return authToken != null && !authToken.isBlank();
    }

    public boolean hasClientCredentials() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    private static boolean startsWithMock(String value) {
        return value != null && value.trim().toLowerCase().startsWith("mock");
    }

    private static boolean containsDemo(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase();
        return lower.contains("mock") || lower.contains("demo");
    }
}
