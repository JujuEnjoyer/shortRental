package com.rental.shortrental.booking.infrastructure;

import com.rental.shortrental.booking.application.IcaService;
import com.rental.shortrental.booking.infrastructure.entity.Calendar;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.CalendarRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.security.SafeExternalUrlValidator;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landlord/calendar-configs")
public class LandlordCalendarController {

    private final CalendarRepository calendarRepository;
    private final LandlordAccess landlordAccess;
    private final IcaService icaService;
    private final SafeExternalUrlValidator safeExternalUrlValidator;

    public LandlordCalendarController(
            CalendarRepository calendarRepository,
            LandlordAccess landlordAccess,
            IcaService icaService,
            SafeExternalUrlValidator safeExternalUrlValidator
    ) {
        this.calendarRepository = calendarRepository;
        this.landlordAccess = landlordAccess;
        this.icaService = icaService;
        this.safeExternalUrlValidator = safeExternalUrlValidator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Calendar create(Authentication authentication, @RequestBody CreateCalendarConfigRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        Property property = landlordAccess.requireOwnedProperty(landlord, request.propertyId());

        Calendar calendar = new Calendar();
        calendar.setProperty(property);
        calendar.setSource(request.source());
        calendar.setIcaUrl(safeExternalUrlValidator.requireSafeHttpUrl(request.icaUrl(), "icaUrl").toString());
        calendar.setEnabled(request.enabled() == null || request.enabled());
        return calendarRepository.save(calendar);
    }

    @GetMapping
    public List<Calendar> mine(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return calendarRepository.findByProperty_User_Id(landlord.getId());
    }

    @PostMapping("/sync/{propertyId}")
    public Map<String, Object> syncProperty(Authentication authentication, @PathVariable Long propertyId) {
        User landlord = landlordAccess.requireLandlord(authentication);
        landlordAccess.requireOwnedProperty(landlord, propertyId);
        int imported = icaService.syncProperty(propertyId);
        return Map.of("propertyId", propertyId, "imported", imported);
    }

    public record CreateCalendarConfigRequest(
            Long propertyId,
            ExternalPlatform source,
            String icaUrl,
            Boolean enabled
    ) {
    }
}
