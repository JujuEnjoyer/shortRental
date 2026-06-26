package com.rental.shortrental.booking.application;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.BookingSource;
import com.rental.shortrental.booking.infrastructure.entity.BookingStatus;
import com.rental.shortrental.booking.infrastructure.entity.Calendar;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.booking.infrastructure.repository.CalendarRepository;
import com.rental.shortrental.security.SafeExternalUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;


@Service
public class IcaService {
    private static final Logger log = LoggerFactory.getLogger(IcaService.class);

    private final CalendarRepository calendarRepo;
    private final BookingRepository bookingRepo;
    private final SafeExternalUrlValidator safeExternalUrlValidator;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public IcaService(
            CalendarRepository calendarRepo,
            BookingRepository bookingRepo,
            SafeExternalUrlValidator safeExternalUrlValidator,
            @Value("${app.security.external-url.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.security.external-url.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this.calendarRepo = calendarRepo;
        this.bookingRepo = bookingRepo;
        this.safeExternalUrlValidator = safeExternalUrlValidator;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Scheduled(fixedDelayString = "${app.sync.fixed-delay-ms:120000}")
    public void syncAll() {
        List<Calendar> calendars = calendarRepo.findByEnabledTrue();
        for (Calendar calendar : calendars) {
            syncCalendar(calendar);
        }
    }

    public int syncProperty(Long propertyId) {
        int imported = 0;
        for (Calendar calendar : calendarRepo.findByPropertyId(propertyId)) {
            if (!calendar.isEnabled()) {
                continue;
            }
            imported += syncCalendar(calendar);
        }
        return imported;
    }

    private int syncCalendar(Calendar calendar) {
        int imported = 0;
        try (InputStream stream = openIcaStream(calendar.getIcaUrl())) {
            ICalendar ical = Biweekly.parse(stream).first();
            if (ical == null) {
                return 0;
            }

            for (VEvent event : ical.getEvents()) {
                if (event.getDateStart() == null || event.getDateEnd() == null) {
                    continue;
                }
                LocalDate start = event.getDateStart().getValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                LocalDate end = event.getDateEnd().getValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                if (!start.isBefore(end)) {
                    continue;
                }

                boolean alreadyImported = bookingRepo.existsByPropertyIdAndSourceAndStartDateAndEndDate(
                        calendar.getProperty().getId(),
                        toBookingSource(calendar.getSource()),
                        start,
                        end
                );
                if (alreadyImported) {
                    continue;
                }

                Booking booking = new Booking();
                booking.setProperty(calendar.getProperty());
                booking.setSource(toBookingSource(calendar.getSource()));
                booking.setStartDate(start);
                booking.setEndDate(end);
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepo.save(booking);
                imported++;
            }
        } catch (Exception e) {
            log.warn("Failed to sync ICS for calendar config id={}", calendar.getId(), e);
        }
        return imported;
    }

    private InputStream openIcaStream(String icaUrl) throws Exception {
        URI uri = safeExternalUrlValidator.requireSafeHttpUrl(icaUrl, "icaUrl");
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        return connection.getInputStream();
    }

    private BookingSource toBookingSource(ExternalPlatform platform) {
        if (platform == null) {
            return BookingSource.ICAL;
        }
        return switch (platform) {
            case AVITO -> BookingSource.AVITO;
            case SUTOCHNO -> BookingSource.SUTOCHNO;
            case CIAN -> BookingSource.CIAN;
            case YANDEX_TRAVEL -> BookingSource.YANDEX_TRAVEL;
            case OSTROVOK -> BookingSource.OSTROVOK;
            case HOSTAWAY -> BookingSource.HOSTAWAY;
            case CHANNEX -> BookingSource.CHANNEX;
            case GUESTY -> BookingSource.GUESTY;
            case BOOKING -> BookingSource.BOOKING;
            case AIRBNB -> BookingSource.AIRBNB;
            case OTHER -> BookingSource.OTHER;
        };
    }
}
