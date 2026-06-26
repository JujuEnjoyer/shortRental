package com.rental.shortrental.integration.partner.messaging;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LivePartnerMessagingClientRegistry {
    private final List<LivePartnerMessagingClient> clients;

    public LivePartnerMessagingClientRegistry(List<LivePartnerMessagingClient> clients) {
        this.clients = List.copyOf(clients);
    }

    public Optional<LivePartnerMessagingClient> find(ExternalPlatform platform) {
        return clients.stream()
                .filter(c -> c.platform() == platform)
                .findFirst();
    }

    public List<LivePartnerMessagingClient> all() {
        return clients;
    }
}
