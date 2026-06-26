package com.rental.shortrental.integration.partner;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/partner")
public class PartnerApiIntegrationController {
    private final PartnerApiClientRegistry registry;

    public PartnerApiIntegrationController(PartnerApiClientRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/verify")
    public ResponseEntity<PartnerApiCheckResult> verify(@RequestBody PartnerApiVerifyRequest request) {
        if (request.platform() == null) {
            return ResponseEntity.badRequest().body(new PartnerApiCheckResult(
                    false,
                    null,
                    "unknown",
                    "ERROR",
                    "platform required",
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.Map.of()
            ));
        }
        PartnerApiCredentials credentials = new PartnerApiCredentials(
                request.platform(),
                request.authToken(),
                request.clientId(),
                request.clientSecret(),
                request.platformUserId(),
                request.inboundEndpoint(),
                request.outboundEndpoint()
        );
        PartnerApiCheckResult result = registry.require(request.platform()).verify(credentials);
        return result.success() ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    public record PartnerApiVerifyRequest(
            ExternalPlatform platform,
            String authToken,
            String clientId,
            String clientSecret,
            String platformUserId,
            String inboundEndpoint,
            String outboundEndpoint
    ) {
    }
}
