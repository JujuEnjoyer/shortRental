package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessagingPortRegistryTest {

    @Test
    void requirePortReturnsFirstSupportingAdapter() {
        ExternalIntegrationConfig config = config(ExternalPlatform.AVITO);
        ExternalMessagingPort unsupported = mock(ExternalMessagingPort.class);
        ExternalMessagingPort supported = mock(ExternalMessagingPort.class);
        when(unsupported.supports(config)).thenReturn(false);
        when(supported.supports(config)).thenReturn(true);

        MessagingPortRegistry registry = new MessagingPortRegistry(List.of(unsupported, supported));

        assertThat(registry.requirePort(config)).isSameAs(supported);
    }

    @Test
    void requirePortFailsWhenNoAdapterSupportsConfig() {
        ExternalIntegrationConfig config = config(ExternalPlatform.CIAN);
        ExternalMessagingPort unsupported = mock(ExternalMessagingPort.class);
        when(unsupported.supports(config)).thenReturn(false);

        MessagingPortRegistry registry = new MessagingPortRegistry(List.of(unsupported));

        assertThatThrownBy(() -> registry.requirePort(config))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No messaging adapter");
    }

    private static ExternalIntegrationConfig config(ExternalPlatform platform) {
        ExternalIntegrationConfig config = new ExternalIntegrationConfig();
        config.setPlatform(platform);
        config.setMode(IntegrationMode.OPEN_API);
        config.setEnabled(true);
        return config;
    }
}
