package com.rental.shortrental.booking.infrastructure;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Date;

@RestController
@RequestMapping("/api/calendar-export")
public class CalendarExportController {

    private final BookingRepository bookingRepository;

    public CalendarExportController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @GetMapping(value = "/{propertyId}.ics", produces = "text/calendar")
    public String export(@PathVariable Long propertyId) {
        ICalendar calendar = new ICalendar();
        for (Booking booking : bookingRepository.findByPropertyId(propertyId)) {
            if (booking.getStartDate() == null || booking.getEndDate() == null) {
                continue;
            }
            VEvent event = new VEvent();
            event.setSummary("Booked");
            event.setDateStart(Date.from(booking.getStartDate().atStartOfDay(ZoneId.systemDefault()).toInstant()), true);
            event.setDateEnd(Date.from(booking.getEndDate().atStartOfDay(ZoneId.systemDefault()).toInstant()), true);
            calendar.addEvent(event);
        }
        return Biweekly.write(calendar).go();
    }
}
