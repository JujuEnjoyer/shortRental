package com.rental.shortrental.booking.application;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.BookingSource;
import com.rental.shortrental.booking.infrastructure.entity.BookingStatus;
import com.rental.shortrental.booking.infrastructure.entity.Calendar;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.booking.infrastructure.repository.CalendarRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.security.SafeExternalUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IcaServiceTest {

    @Mock
    private CalendarRepository calendarRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SafeExternalUrlValidator safeExternalUrlValidator;

    private IcaService icaService;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        icaService = new IcaService(calendarRepository, bookingRepository, safeExternalUrlValidator, 5000, 10000);
    }

    @Test
    void syncPropertyImportsValidIcsEvent() throws Exception {
        Path ics = tempDir.resolve("calendar.ics");
        Files.writeString(ics, """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:test-1
                DTSTART;VALUE=DATE:20260610
                DTEND;VALUE=DATE:20260614
                SUMMARY:Booked
                END:VEVENT
                END:VCALENDAR
                """);
        Property property = new Property();
        property.setId(10L);
        Calendar calendar = new Calendar();
        calendar.setId(5L);
        calendar.setProperty(property);
        calendar.setEnabled(true);
        calendar.setSource(ExternalPlatform.AVITO);
        calendar.setIcaUrl(ics.toUri().toString());

        when(calendarRepository.findByPropertyId(property.getId())).thenReturn(List.of(calendar));
        when(safeExternalUrlValidator.requireSafeHttpUrl(calendar.getIcaUrl(), "icaUrl"))
                .thenReturn(ics.toUri());
        when(bookingRepository.existsByPropertyIdAndSourceAndStartDateAndEndDate(
                property.getId(),
                BookingSource.AVITO,
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 14)
        )).thenReturn(false);

        int imported = icaService.syncProperty(property.getId());

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();
        assertThat(saved.getProperty()).isSameAs(property);
        assertThat(saved.getSource()).isEqualTo(BookingSource.AVITO);
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 6, 14));
    }

    @Test
    void syncPropertySkipsAlreadyImportedEvent() throws Exception {
        Path ics = tempDir.resolve("calendar.ics");
        Files.writeString(ics, """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:test-1
                DTSTART;VALUE=DATE:20260610
                DTEND;VALUE=DATE:20260614
                SUMMARY:Booked
                END:VEVENT
                END:VCALENDAR
                """);
        Property property = new Property();
        property.setId(10L);
        Calendar calendar = new Calendar();
        calendar.setProperty(property);
        calendar.setEnabled(true);
        calendar.setSource(ExternalPlatform.AVITO);
        calendar.setIcaUrl(ics.toUri().toString());

        when(calendarRepository.findByPropertyId(property.getId())).thenReturn(List.of(calendar));
        when(safeExternalUrlValidator.requireSafeHttpUrl(calendar.getIcaUrl(), "icaUrl"))
                .thenReturn(ics.toUri());
        when(bookingRepository.existsByPropertyIdAndSourceAndStartDateAndEndDate(
                any(), any(), any(), any()
        )).thenReturn(true);

        int imported = icaService.syncProperty(property.getId());

        assertThat(imported).isZero();
    }
}
