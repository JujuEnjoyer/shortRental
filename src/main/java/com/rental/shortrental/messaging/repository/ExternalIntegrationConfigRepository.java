package com.rental.shortrental.messaging.repository;

import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalIntegrationConfigRepository extends JpaRepository<ExternalIntegrationConfig, Long> {
    List<ExternalIntegrationConfig> findByPropertyUserId(Long ownerId);
    List<ExternalIntegrationConfig> findByEnabledTrue();
}
