package com.rental.shortrental.booking.application;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.Calendar;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.booking.infrastructure.repository.CalendarRepository;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;


@Service
public class IcalService {

    private final CalendarRepository calendarRepo;
    private final BookingRepository bookingRepo;

    public IcalService(CalendarRepository calendarRepo, BookingRepository bookingRepo) {
        this.calendarRepo = calendarRepo;
        this.bookingRepo = bookingRepo;
    }

    public void sync(Long propertyId) throws Exception {

        List<Calendar> calendars =
                calendarRepo.findAll().stream()
                        .filter(c -> c.getProperty().getId().equals(propertyId))
                        .toList();

        for (Calendar cal : calendars) {

            InputStream stream = new URL(cal.getIcaUrl()).openStream();

            ICalendar ical = Biweekly.parse(stream).first();

            for (VEvent event : ical.getEvents()) {

                LocalDate start = event.getDateStart().getValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                LocalDate end = event.getDateEnd().getValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                Booking booking = new Booking();
                booking.setProperty(cal.getProperty());
                booking.setSource(cal.getSource());
                booking.setStartDate(start);
                booking.setEndDate(end);

                bookingRepo.save(booking);
            }
        }
    }
}