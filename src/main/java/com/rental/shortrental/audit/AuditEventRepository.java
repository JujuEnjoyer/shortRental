package com.rental.shortrental.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop100ByLandlordIdOrderByCreatedAtDesc(Long landlordId);
}
