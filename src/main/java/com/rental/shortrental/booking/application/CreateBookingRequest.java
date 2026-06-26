package com.rental.shortrental.booking.application;

import com.rental.shortrental.booking.infrastructure.entity.BookingSource;

import java.time.LocalDate;

public record CreateBookingRequest(
        Long propertyId,
        Long guestId,
        LocalDate startDate,
        LocalDate endDate,
        BookingSource source
) {
}
