package com.rental.shortrental.audit;

import com.rental.shortrental.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User landlord, User actor, AuditAction action,
                       String targetType, Long targetId, String summary) {
        record(landlord, actor, action, targetType, targetId, summary, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User landlord, User actor, AuditAction action,
                       String targetType, Long targetId, String summary, String metadata) {
        AuditEvent event = new AuditEvent();
        event.setLandlord(landlord);
        event.setActor(actor);
        event.setActorEmail(actor != null ? actor.getEmail() : null);
        event.setActorRole(actor != null ? actor.getRole() : null);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setSummary(trim(summary, 512));
        event.setMetadata(trim(metadata, 2000));
        event.setCreatedAt(Instant.now());
        auditEventRepository.save(event);
    }

    private static String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
