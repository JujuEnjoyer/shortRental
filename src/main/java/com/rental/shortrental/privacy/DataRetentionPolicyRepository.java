package com.rental.shortrental.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DataRetentionPolicyRepository extends JpaRepository<DataRetentionPolicy, Long> {
    Optional<DataRetentionPolicy> findByLandlordId(Long landlordId);
}
