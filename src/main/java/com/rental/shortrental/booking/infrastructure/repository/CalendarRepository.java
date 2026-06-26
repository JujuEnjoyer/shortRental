package com.rental.shortrental.booking.infrastructure.repository;

import com.rental.shortrental.booking.infrastructure.entity.Calendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {
    List<Calendar> findByPropertyId(Long propertyId);
    List<Calendar> findByEnabledTrue();
    List<Calendar> findByProperty_User_Id(Long ownerId);
}
