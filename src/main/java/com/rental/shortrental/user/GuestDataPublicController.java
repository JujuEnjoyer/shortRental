package com.rental.shortrental.user;

import com.rental.shortrental.audit.AuditAction;
import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.common.util.FileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/public/guest-data")
public class GuestDataPublicController {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;

    public GuestDataPublicController(
            UserRepository userRepository,
            FileStorageService fileStorageService,
            BookingRepository bookingRepository,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
    }

    @GetMapping("/{token}/status")
    public GuestDataStatusResponse status(@PathVariable String token) {
        return userRepository.findByDataCollectionToken(token)
                .map(u -> {
                    if (u.getDataCollectionTokenExpiresAt() == null
                            || Instant.now().isAfter(u.getDataCollectionTokenExpiresAt())) {
                        return new GuestDataStatusResponse(false, true, "Срок ссылки истёк — попросите арендодателя выпустить новую.");
                    }
                    return new GuestDataStatusResponse(true, false, null);
                })
                .orElseGet(() -> new GuestDataStatusResponse(false, true, "Ссылка недействительна."));
    }

    @PostMapping("/{token}")
    @Transactional
    public void submit(@PathVariable String token, @RequestBody GuestDataSubmission body) {
        User guest = userRepository.findByDataCollectionToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid link"));
        if (guest.getDataCollectionTokenExpiresAt() == null
                || Instant.now().isAfter(guest.getDataCollectionTokenExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Link expired");
        }
        if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a guest account");
        }
        guest.setPassportSeries(trimToNull(body.passportSeries()));
        guest.setPassportNumber(trimToNull(body.passportNumber()));
        guest.setPassportIssuedBy(trimToNull(body.passportIssuedBy()));
        guest.setPassportIssueDate(body.passportIssueDate());
        guest.setBirthDate(body.birthDate());
        guest.setGuestPhone(trimToNull(body.guestPhone()));
        userRepository.save(guest);
        landlordForGuest(guest).ifPresent(landlord -> auditService.record(
                landlord,
                guest,
                AuditAction.GUEST_DATA_SUBMITTED,
                "GUEST",
                guest.getId(),
                "Гость отправил данные по гостевой ссылке"
        ));
    }

    @PostMapping("/{token}/photos")
    @Transactional
    public Map<String, String> uploadPhotos(
            @PathVariable String token,
            @RequestParam(value = "photo1", required = false) MultipartFile photo1,
            @RequestParam(value = "photo2", required = false) MultipartFile photo2
    ) {
        User guest = findByToken(token);
        fileStorageService.delete(guest.getPassportPhoto1());
        fileStorageService.delete(guest.getPassportPhoto2());
        if (photo1 != null && !photo1.isEmpty()) {
            guest.setPassportPhoto1(fileStorageService.store(photo1, "pp1"));
        }
        if (photo2 != null && !photo2.isEmpty()) {
            guest.setPassportPhoto2(fileStorageService.store(photo2, "pp2"));
        }
        userRepository.save(guest);
        landlordForGuest(guest).ifPresent(landlord -> auditService.record(
                landlord,
                guest,
                AuditAction.GUEST_PHOTOS_UPLOADED,
                "GUEST",
                guest.getId(),
                "Гость загрузил фото документа по гостевой ссылке"
        ));
        return Map.of("status", "ok");
    }

    private User findByToken(String token) {
        User guest = userRepository.findByDataCollectionToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid link"));
        if (guest.getDataCollectionTokenExpiresAt() == null
                || Instant.now().isAfter(guest.getDataCollectionTokenExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Link expired");
        }
        if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a guest account");
        }
        return guest;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private java.util.Optional<User> landlordForGuest(User guest) {
        return bookingRepository.findFirstByGuestIdOrderByStartDateDesc(guest.getId())
                .map(Booking::getProperty)
                .map(property -> property.getUser());
    }

    public record GuestDataStatusResponse(boolean valid, boolean expired, String message) {
    }

    public record GuestDataSubmission(
            String passportSeries,
            String passportNumber,
            String passportIssuedBy,
            java.time.LocalDate passportIssueDate,
            java.time.LocalDate birthDate,
            String guestPhone
    ) {
    }
}
