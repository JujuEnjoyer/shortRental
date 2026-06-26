package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class PartnerApiClientRegistry {
    private final List<PartnerApiClient> clients;

    public PartnerApiClientRegistry(List<PartnerApiClient> clients) {
        this.clients = List.copyOf(clients);
    }

    public PartnerApiClient require(ExternalPlatform platform) {
        return clients.stream()
                .filter(c -> c.supports(platform))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No partner API client for " + platform
                ));
    }
}
