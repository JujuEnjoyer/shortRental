package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;

/**
 * API view for integration configs — never exposes {@code clientSecret}.
 */
public record IntegrationConfigResponse(
        Long id,
        Long propertyId,
        String propertyName,
        ExternalPlatform platform,
        IntegrationMode mode,
        String platformUserId,
        String inboundEndpoint,
        String outboundEndpoint,
        boolean enabled,
        boolean authTokenConfigured,
        boolean clientIdConfigured,
        boolean clientSecretConfigured
) {
    public static IntegrationConfigResponse fromEntity(ExternalIntegrationConfig c) {
        String cid = c.getClientId();
        String sec = c.getClientSecret();
        String token = c.getAuthToken();
        return new IntegrationConfigResponse(
                c.getId(),
                c.getProperty().getId(),
                c.getProperty().getName(),
                c.getPlatform(),
                c.getMode(),
                c.getPlatformUserId(),
                c.getInboundEndpoint(),
                c.getOutboundEndpoint(),
                c.isEnabled(),
                token != null && !token.isBlank(),
                cid != null && !cid.isBlank(),
                sec != null && !sec.isBlank()
        );
    }
}
