package com.rental.shortrental.booking.infrastructure;

import com.rental.shortrental.audit.AuditAction;
import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.booking.application.BookingService;
import com.rental.shortrental.booking.application.CreateBookingRequest;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/landlord/bookings")
public class LandlordBookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final LandlordAccess landlordAccess;
    private final AuditService auditService;

    public LandlordBookingController(
            BookingService bookingService,
            BookingRepository bookingRepository,
            LandlordAccess landlordAccess,
            AuditService auditService
    ) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.landlordAccess = landlordAccess;
        this.auditService = auditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Booking create(Authentication authentication, @RequestBody CreateBookingRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        landlordAccess.requireOwnedProperty(landlord, request.propertyId());
        Booking booking = bookingService.createBooking(request);
        auditService.record(
                landlord,
                landlord,
                AuditAction.BOOKING_CREATED,
                "BOOKING",
                booking.getId(),
                "Создано бронирование по объекту #" + request.propertyId()
        );
        return booking;
    }

    @GetMapping
    public List<Booking> list(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return bookingRepository.findByProperty_User_Id(landlord.getId());
    }

    @GetMapping("/{id}")
    public Booking getById(Authentication authentication, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(authentication);
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (booking.getProperty() == null || booking.getProperty().getUser() == null
                || !booking.getProperty().getUser().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking not accessible");
        }
        return booking;
    }
}
