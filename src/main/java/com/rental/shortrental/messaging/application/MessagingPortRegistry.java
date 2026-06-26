package com.rental.shortrental.messaging.application;

import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MessagingPortRegistry {
    private final List<ExternalMessagingPort> ports;

    public MessagingPortRegistry(List<ExternalMessagingPort> ports) {
        this.ports = List.copyOf(ports);
    }

    public ExternalMessagingPort requirePort(ExternalIntegrationConfig config) {
        return ports.stream()
                .filter(p -> p.supports(config))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No messaging adapter for platform " + config.getPlatform()
                                + " and mode " + config.getMode()
                ));
    }

    public List<MessagingAdapterDescriptor> descriptors() {
        return ports.stream()
                .map(ExternalMessagingPort::descriptor)
                .toList();
    }
}
