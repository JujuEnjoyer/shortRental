package com.rental.shortrental.user;

import com.rental.shortrental.audit.AuditAction;
import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.messaging.application.TemplateRenderService;
import com.rental.shortrental.security.LandlordAccess;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landlord/guests")
public class LandlordGuestController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LandlordAccess landlordAccess;
    private final BookingRepository bookingRepository;
    private final TemplateRenderService templateRenderService;
    private final AuditService auditService;

    public LandlordGuestController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LandlordAccess landlordAccess,
            BookingRepository bookingRepository,
            TemplateRenderService templateRenderService,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.landlordAccess = landlordAccess;
        this.bookingRepository = bookingRepository;
        this.templateRenderService = templateRenderService;
        this.auditService = auditService;
    }

    private User requireGuestOfLandlord(Authentication auth, Long guestId) {
        User landlord = landlordAccess.requireLandlord(auth);
        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));
        if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a guest account");
        }
        if (!bookingRepository.existsByGuestAndLandlord(guestId, landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest has no booking with your properties");
        }
        return guest;
    }

    @GetMapping
    public List<GuestSummaryResponse> list(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return userRepository.findGuestsWithBookingForLandlord(landlord.getId()).stream()
                .map(u -> new GuestSummaryResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getName(),
                        u.getSurname(),
                        u.getPassportNumber() != null && !u.getPassportNumber().isBlank()
                ))
                .toList();
    }

    @PostMapping("/{guestId}/data-link")
    public Map<String, String> issueDataLink(Authentication authentication, @PathVariable Long guestId) {
        User landlord = landlordAccess.requireLandlord(authentication);
        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));
        if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a guest account");
        }
        if (!bookingRepository.existsByGuestAndLandlord(guestId, landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest has no booking with your properties");
        }
        templateRenderService.ensureGuestDataToken(guest);
        userRepository.save(guest);
        auditService.record(
                landlord,
                landlord,
                AuditAction.GUEST_DATA_LINK_ISSUED,
                "GUEST",
                guest.getId(),
                "Выпущена ссылка для заполнения данных гостя"
        );
        return Map.of(
                "url", templateRenderService.guestFormUrl(guest),
                "expiresAt", guest.getDataCollectionTokenExpiresAt().toString()
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User createGuest(Authentication authentication, @RequestBody CreateGuestRequest request) {
        landlordAccess.requireLandlord(authentication);
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Guest email already exists");
        }
        User guest = new User();
        guest.setEmail(request.email().trim().toLowerCase());
        guest.setPassword(passwordEncoder.encode(request.password()));
        guest.setName(request.name());
        guest.setSurname(request.surname());
        guest.setRole("GUEST");
        User saved = userRepository.save(guest);
        User landlord = landlordAccess.requireLandlord(authentication);
        auditService.record(
                landlord,
                landlord,
                AuditAction.GUEST_CREATED,
                "GUEST",
                saved.getId(),
                "Создан профиль гостя"
        );
        return saved;
    }

    @GetMapping("/{guestId}/passport")
    public PassportDetailResponse getPassportDetail(Authentication auth, @PathVariable Long guestId) {
        User landlord = landlordAccess.requireLandlord(auth);
        User guest = requireGuestOfLandlord(auth, guestId);
        auditService.record(
                landlord,
                landlord,
                AuditAction.PASSPORT_VIEWED,
                "GUEST",
                guest.getId(),
                "Открыты паспортные данные гостя"
        );
        return new PassportDetailResponse(
                guest.getId(),
                guest.getPassportSeries(),
                guest.getPassportNumber(),
                guest.getPassportIssuedBy(),
                guest.getPassportIssueDate(),
                guest.getBirthDate(),
                guest.getGuestPhone(),
                guest.getPassportPhoto1(),
                guest.getPassportPhoto2(),
                guest.getPassportVerified()
        );
    }

    @PostMapping("/{guestId}/verify")
    public Map<String, String> verifyPassport(Authentication auth, @PathVariable Long guestId,
                                              @RequestBody VerifyRequest request) {
        User guest = requireGuestOfLandlord(auth, guestId);
        String status = request.status();
        if (!"VERIFIED".equals(status) && !"REJECTED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be VERIFIED or REJECTED");
        }
        guest.setPassportVerified(status);
        userRepository.save(guest);
        User landlord = landlordAccess.requireLandlord(auth);
        auditService.record(
                landlord,
                landlord,
                "VERIFIED".equals(status) ? AuditAction.PASSPORT_VERIFIED : AuditAction.PASSPORT_REJECTED,
                "GUEST",
                guest.getId(),
                "VERIFIED".equals(status) ? "Паспортные данные подтверждены" : "Паспортные данные отклонены"
        );
        return Map.of("status", status);
    }

    public record CreateGuestRequest(String email, String password, String name, String surname) {
    }

    public record GuestSummaryResponse(
            Long id,
            String email,
            String name,
            String surname,
            boolean passportFilled
    ) {
    }

    public record PassportDetailResponse(
            Long id,
            String passportSeries,
            String passportNumber,
            String passportIssuedBy,
            java.time.LocalDate passportIssueDate,
            java.time.LocalDate birthDate,
            String guestPhone,
            String passportPhoto1,
            String passportPhoto2,
            String passportVerified
    ) {
    }

    public record VerifyRequest(String status) {
    }
}
