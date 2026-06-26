package com.rental.shortrental.booking.application;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.BookingSource;
import com.rental.shortrental.booking.infrastructure.entity.BookingStatus;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    public BookingService(
            BookingRepository bookingRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    public Booking createBooking(CreateBookingRequest request) {
        if (!request.startDate().isBefore(request.endDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }

        Property property = propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));

        User guest = userRepository.findById(request.guestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));

        boolean overlaps = bookingRepository.existsByPropertyIdAndStartDateLessThanAndEndDateGreaterThan(
                request.propertyId(),
                request.endDate(),
                request.startDate()
        );

        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dates already booked");
        }

        Booking booking = new Booking();
        booking.setProperty(property);
        booking.setGuest(guest);
        booking.setStartDate(request.startDate());
        booking.setEndDate(request.endDate());
        booking.setSource(request.source() == null ? BookingSource.SITE : request.source());
        booking.setStatus(BookingStatus.CONFIRMED);
        return bookingRepository.save(booking);
    }
}
