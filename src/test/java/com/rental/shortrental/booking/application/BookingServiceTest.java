package com.rental.shortrental.booking.application;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.BookingSource;
import com.rental.shortrental.booking.infrastructure.entity.BookingStatus;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBookingRejectsInvalidDates() {
        CreateBookingRequest request = new CreateBookingRequest(
                1L,
                2L,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 10),
                BookingSource.SITE
        );

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("startDate must be before endDate");
    }

    @Test
    void createBookingRejectsOverlappingDates() {
        Property property = property(1L);
        User guest = guest(2L);
        CreateBookingRequest request = new CreateBookingRequest(
                property.getId(),
                guest.getId(),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 14),
                BookingSource.AVITO
        );
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(bookingRepository.existsByPropertyIdAndStartDateLessThanAndEndDateGreaterThan(
                property.getId(), request.endDate(), request.startDate()
        )).thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Dates already booked");
    }

    @Test
    void createBookingSavesConfirmedBooking() {
        Property property = property(1L);
        User guest = guest(2L);
        CreateBookingRequest request = new CreateBookingRequest(
                property.getId(),
                guest.getId(),
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 14),
                null
        );
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.createBooking(request);

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getProperty()).isSameAs(property);
        assertThat(saved.getGuest()).isSameAs(guest);
        assertThat(saved.getSource()).isEqualTo(BookingSource.SITE);
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.getStartDate()).isEqualTo(request.startDate());
        assertThat(saved.getEndDate()).isEqualTo(request.endDate());
    }

    private static Property property(Long id) {
        Property property = new Property();
        property.setId(id);
        property.setName("Demo flat");
        property.setAddress("Moscow");
        return property;
    }

    private static User guest(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("guest@example.test");
        user.setRole("GUEST");
        return user;
    }
}
