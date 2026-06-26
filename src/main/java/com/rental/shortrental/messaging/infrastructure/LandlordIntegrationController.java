package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import com.rental.shortrental.messaging.application.MessagingAdapterDescriptor;
import com.rental.shortrental.messaging.application.MessagingPortRegistry;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.security.SafeExternalUrlValidator;
import com.rental.shortrental.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/landlord/integrations")
public class LandlordIntegrationController {
    private final ExternalIntegrationConfigRepository integrationRepository;
    private final LandlordAccess landlordAccess;
    private final MessagingPortRegistry messagingPortRegistry;
    private final SafeExternalUrlValidator safeExternalUrlValidator;

    public LandlordIntegrationController(
            ExternalIntegrationConfigRepository integrationRepository,
            LandlordAccess landlordAccess,
            MessagingPortRegistry messagingPortRegistry,
            SafeExternalUrlValidator safeExternalUrlValidator
    ) {
        this.integrationRepository = integrationRepository;
        this.landlordAccess = landlordAccess;
        this.messagingPortRegistry = messagingPortRegistry;
        this.safeExternalUrlValidator = safeExternalUrlValidator;
    }

    @GetMapping
    public List<IntegrationConfigResponse> list(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return integrationRepository.findByPropertyUserId(landlord.getId()).stream()
                .map(IntegrationConfigResponse::fromEntity)
                .toList();
    }

    @GetMapping("/adapters")
    public List<MessagingAdapterDescriptor> adapters(Authentication authentication) {
        landlordAccess.requireLandlord(authentication);
        return messagingPortRegistry.descriptors();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationConfigResponse create(Authentication authentication, @RequestBody CreateIntegrationRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        if (request.platform() == null || request.mode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform and mode are required");
        }
        Property property = landlordAccess.requireOwnedProperty(landlord, request.propertyId());

        ExternalIntegrationConfig config = new ExternalIntegrationConfig();
        config.setProperty(property);
        config.setPlatform(request.platform());
        config.setMode(request.mode());
        config.setInboundEndpoint(normalizeEndpoint(request.inboundEndpoint(), "inboundEndpoint"));
        config.setOutboundEndpoint(normalizeEndpoint(request.outboundEndpoint(), "outboundEndpoint"));
        config.setAuthToken(emptyToNull(request.authToken()));
        String platformUserId = request.platformUserId();
        config.setPlatformUserId(platformUserId == null || platformUserId.isBlank() ? null : platformUserId.trim());
        config.setClientId(emptyToNull(request.clientId()));
        config.setClientSecret(emptyToNull(request.clientSecret()));
        config.setEnabled(request.enabled() == null || request.enabled());
        ExternalIntegrationConfig saved = integrationRepository.save(config);
        return IntegrationConfigResponse.fromEntity(saved);
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private String normalizeEndpoint(String s, String fieldName) {
        return safeExternalUrlValidator.normalizeOptionalExternalEndpoint(s, fieldName);
    }

    public record CreateIntegrationRequest(
            Long propertyId,
            ExternalPlatform platform,
            IntegrationMode mode,
            String platformUserId,
            String inboundEndpoint,
            String outboundEndpoint,
            String authToken,
            String clientId,
            String clientSecret,
            Boolean enabled
    ) {
    }
}
