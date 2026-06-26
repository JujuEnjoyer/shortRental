package com.rental.shortrental.user;

import com.rental.shortrental.security.LandlordAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/landlord/settings")
public class LandlordSettingsController {

    private final GuestCardConfigRepository configRepository;
    private final UserRepository userRepository;
    private final LandlordAccess landlordAccess;

    public LandlordSettingsController(GuestCardConfigRepository configRepository,
                                      UserRepository userRepository,
                                      LandlordAccess landlordAccess) {
        this.configRepository = configRepository;
        this.userRepository = userRepository;
        this.landlordAccess = landlordAccess;
    }

    /* ========== Guest card config ========== */

    @GetMapping("/guest-card")
    public GuestCardConfig getGuestCardConfig(Authentication auth) {
        User landlord = landlordAccess.requireLandlord(auth);
        return configRepository.findByLandlordId(landlord.getId())
                .orElseGet(() -> {
                    GuestCardConfig cfg = new GuestCardConfig();
                    cfg.setLandlord(landlord);
                    cfg.setEnabled(true);
                    cfg.setFieldsConfigJson(defaultFieldsConfig());
                    return configRepository.save(cfg);
                });
    }

    @PutMapping("/guest-card")
    public GuestCardConfig updateGuestCardConfig(Authentication auth, @RequestBody GuestCardConfig body) {
        User landlord = landlordAccess.requireLandlord(auth);
        GuestCardConfig cfg = configRepository.findByLandlordId(landlord.getId())
                .orElseGet(() -> {
                    GuestCardConfig c = new GuestCardConfig();
                    c.setLandlord(landlord);
                    return c;
                });
        cfg.setEnabled(body.isEnabled());
        if (body.getFieldsConfigJson() != null) {
            cfg.setFieldsConfigJson(body.getFieldsConfigJson());
        }
        if (body.getCustomFieldsJson() != null) {
            cfg.setCustomFieldsJson(body.getCustomFieldsJson());
        }
        return configRepository.save(cfg);
    }

    /* ========== Landlord profile ========== */

    @GetMapping("/profile")
    public User getProfile(Authentication auth) {
        return landlordAccess.requireLandlord(auth);
    }

    @PutMapping("/profile")
    public User updateProfile(Authentication auth, @RequestBody User body) {
        User landlord = landlordAccess.requireLandlord(auth);
        if (body.getName() != null) landlord.setName(body.getName());
        if (body.getSurname() != null) landlord.setSurname(body.getSurname());
        if (body.getPhone() != null) landlord.setPhone(body.getPhone());
        if (body.getLandlordPassportSeries() != null) landlord.setLandlordPassportSeries(body.getLandlordPassportSeries());
        if (body.getLandlordPassportNumber() != null) landlord.setLandlordPassportNumber(body.getLandlordPassportNumber());
        if (body.getLandlordPassportIssuedBy() != null) landlord.setLandlordPassportIssuedBy(body.getLandlordPassportIssuedBy());
        if (body.getLandlordPassportIssueDate() != null) landlord.setLandlordPassportIssueDate(body.getLandlordPassportIssueDate());
        if (body.getLandlordRegistrationAddress() != null) landlord.setLandlordRegistrationAddress(body.getLandlordRegistrationAddress());
        return userRepository.save(landlord);
    }

    private static String defaultFieldsConfig() {
        return """
            [
                {"field":"passportSeries","label":"Серия паспорта","enabled":true,"required":true},
                {"field":"passportNumber","label":"Номер паспорта","enabled":true,"required":true},
                {"field":"passportIssuedBy","label":"Кем выдан","enabled":true,"required":true},
                {"field":"passportIssueDate","label":"Дата выдачи","enabled":true,"required":true},
                {"field":"birthDate","label":"Дата рождения","enabled":true,"required":true},
                {"field":"guestPhone","label":"Телефон","enabled":true,"required":false}
            ]
            """.trim();
    }
}
