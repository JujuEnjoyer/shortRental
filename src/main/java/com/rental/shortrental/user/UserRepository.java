package com.rental.shortrental.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByDataCollectionToken(String token);

    @Query("SELECT DISTINCT b.guest FROM Booking b WHERE b.property.user.id = :landlordId AND b.guest IS NOT NULL")
    List<User> findGuestsWithBookingForLandlord(@Param("landlordId") Long landlordId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Booking b "
            + "WHERE b.property.user.id = :landlordId AND b.guest IS NOT NULL "
            + "AND (b.guest.passportPhoto1 = :photoName OR b.guest.passportPhoto2 = :photoName)")
    boolean existsPassportPhotoForLandlord(@Param("landlordId") Long landlordId, @Param("photoName") String photoName);

    Optional<User> findFirstByRoleAndDataCollectionTokenIsNotNull(String role);
}
