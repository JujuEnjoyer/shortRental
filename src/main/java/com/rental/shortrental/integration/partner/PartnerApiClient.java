package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;

public interface PartnerApiClient {
    boolean supports(ExternalPlatform platform);

    PartnerApiCheckResult verify(PartnerApiCredentials credentials);
}
