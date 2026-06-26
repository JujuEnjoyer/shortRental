package com.rental.shortrental.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestCardConfigRepository extends JpaRepository<GuestCardConfig, Long> {
    Optional<GuestCardConfig> findByLandlordId(Long landlordId);
}
