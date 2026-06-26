package com.rental.shortrental.booking.infrastructure.repository;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.BookingSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByPropertyId(Long propertyId);

    List<Booking> findByProperty_User_Id(Long ownerId);

    List<Booking> findByProperty_User_IdAndEndDateBeforeAndGuestIsNotNull(
            Long ownerId,
            java.time.LocalDate endDate
    );

    boolean existsByPropertyIdAndStartDateLessThanAndEndDateGreaterThan(
            Long propertyId,
            java.time.LocalDate endDate,
            java.time.LocalDate startDate
    );

    boolean existsByPropertyIdAndSourceAndStartDateAndEndDate(
            Long propertyId,
            BookingSource source,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    );

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Booking b "
            + "WHERE b.guest.id = :guestId AND b.property.user.id = :landlordId")
    boolean existsByGuestAndLandlord(@Param("guestId") Long guestId, @Param("landlordId") Long landlordId);

    Optional<Booking> findFirstByGuestIdOrderByStartDateDesc(Long guestId);
}
