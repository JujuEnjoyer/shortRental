package com.rental.shortrental.audit;

import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/landlord/audit")
public class LandlordAuditController {

    private final AuditEventRepository auditEventRepository;
    private final LandlordAccess landlordAccess;

    public LandlordAuditController(AuditEventRepository auditEventRepository, LandlordAccess landlordAccess) {
        this.auditEventRepository = auditEventRepository;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping
    public List<AuditEventResponse> recent(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return auditEventRepository.findTop100ByLandlordIdOrderByCreatedAtDesc(landlord.getId()).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    public record AuditEventResponse(
            Long id,
            Instant createdAt,
            AuditAction action,
            String actorEmail,
            String actorRole,
            String targetType,
            Long targetId,
            String summary,
            String metadata
    ) {
        static AuditEventResponse from(AuditEvent event) {
            return new AuditEventResponse(
                    event.getId(),
                    event.getCreatedAt(),
                    event.getAction(),
                    event.getActorEmail(),
                    event.getActorRole(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getSummary(),
                    event.getMetadata()
            );
        }
    }
}
