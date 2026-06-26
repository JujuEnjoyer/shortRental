package com.rental.shortrental.test;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.BookingSource;
import com.rental.shortrental.booking.infrastructure.entity.BookingStatus;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.document.application.DocumentLinkService;
import com.rental.shortrental.document.interfaces.DocumentTemplateRepository;
import com.rental.shortrental.messaging.application.MessageSyncService;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import com.rental.shortrental.messaging.entity.ReplyTemplate;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import com.rental.shortrental.messaging.repository.ReplyTemplateRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/landlord/demo")
public class LandlordDemoController {

    private final LandlordAccess landlordAccess;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final ExternalIntegrationConfigRepository integrationRepository;
    private final ReplyTemplateRepository replyTemplateRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final DocumentLinkService documentLinkService;
    private final MessageSyncService messageSyncService;

    public LandlordDemoController(
            LandlordAccess landlordAccess,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            ExternalIntegrationConfigRepository integrationRepository,
            ReplyTemplateRepository replyTemplateRepository,
            DocumentTemplateRepository documentTemplateRepository,
            DocumentLinkService documentLinkService,
            MessageSyncService messageSyncService
    ) {
        this.landlordAccess = landlordAccess;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.integrationRepository = integrationRepository;
        this.replyTemplateRepository = replyTemplateRepository;
        this.documentTemplateRepository = documentTemplateRepository;
        this.documentLinkService = documentLinkService;
        this.messageSyncService = messageSyncService;
    }

    @PostMapping("/seed")
    public Map<String, Object> seed(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        Property property = findOrCreateDemoProperty(landlord);
        User guest = findOrCreateDemoGuest(landlord);
        Booking booking = findOrCreateDemoBooking(property, guest);
        ExternalIntegrationConfig integration = findOrCreateDemoIntegration(property);
        Long documentTemplateId = findDocumentTemplateId(landlord);
        ReplyTemplate replyTemplate = findOrCreateReplyTemplate(landlord, documentTemplateId);

        int imported = messageSyncService.syncOwnerMessages(landlord.getId());
        String guestFormUrl = ensureGuestFormUrl(guest);
        String documentUrl = documentLinkService.createDocumentLink(landlord, guest, documentTemplateId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("landlordId", landlord.getId());
        result.put("propertyId", property.getId());
        result.put("guestId", guest.getId());
        result.put("guestEmail", guest.getEmail());
        result.put("bookingId", booking.getId());
        result.put("integrationId", integration.getId());
        result.put("replyTemplateId", replyTemplate.getId());
        result.put("documentTemplateId", documentTemplateId);
        result.put("externalConversationId", "api-conv-" + integration.getId());
        result.put("guestFormUrl", guestFormUrl);
        result.put("documentUrl", documentUrl);
        result.put("importedMessages", imported);
        return result;
    }

    private Property findOrCreateDemoProperty(User landlord) {
        return propertyRepository.findByUser_Id(landlord.getId()).stream()
                .filter(p -> "Демо-квартира API".equals(p.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Property p = new Property();
                    p.setUser(landlord);
                    p.setName("Демо-квартира API");
                    p.setAddress("Москва, Тверская, 1");
                    return propertyRepository.save(p);
                });
    }

    private User findOrCreateDemoGuest(User landlord) {
        String email = "demo-guest-" + landlord.getId() + "@test.local";
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User guest = new User();
                    guest.setEmail(email);
                    guest.setPassword(passwordEncoder.encode("test123"));
                    guest.setName("Демо");
                    guest.setSurname("Гость");
                    guest.setRole("GUEST");
                    guest.setOnboardingCompleted(true);
                    guest.setDataCollectionToken(UUID.randomUUID().toString());
                    guest.setDataCollectionTokenExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
                    return userRepository.save(guest);
                });
    }

    private Booking findOrCreateDemoBooking(Property property, User guest) {
        LocalDate start = LocalDate.now().plusDays(7);
        LocalDate end = start.plusDays(3);
        return bookingRepository.findByPropertyId(property.getId()).stream()
                .filter(b -> b.getGuest() != null && guest.getId().equals(b.getGuest().getId()))
                .findFirst()
                .orElseGet(() -> {
                    Booking booking = new Booking();
                    booking.setProperty(property);
                    booking.setGuest(guest);
                    booking.setSource(BookingSource.SUTOCHNO);
                    booking.setStatus(BookingStatus.CONFIRMED);
                    booking.setStartDate(start);
                    booking.setEndDate(end);
                    return bookingRepository.save(booking);
                });
    }

    private ExternalIntegrationConfig findOrCreateDemoIntegration(Property property) {
        return integrationRepository.findByPropertyUserId(property.getUser().getId()).stream()
                .filter(i -> property.getId().equals(i.getProperty().getId()))
                .filter(i -> i.getPlatform() == ExternalPlatform.SUTOCHNO)
                .findFirst()
                .orElseGet(() -> {
                    ExternalIntegrationConfig config = new ExternalIntegrationConfig();
                    config.setProperty(property);
                    config.setPlatform(ExternalPlatform.SUTOCHNO);
                    config.setMode(IntegrationMode.OPEN_API);
                    config.setInboundEndpoint("demo:sutochno-api");
                    config.setOutboundEndpoint("demo:safe-reply");
                    config.setAuthToken("demo-token");
                    config.setEnabled(true);
                    return integrationRepository.save(config);
                });
    }

    private Long findDocumentTemplateId(User landlord) {
        return documentTemplateRepository.findByLandlordIdAndDocumentType(landlord.getId(), "CONTRACT").stream()
                .findFirst()
                .or(() -> documentTemplateRepository.findBySystemTrueAndDocumentType("CONTRACT").stream().findFirst())
                .map(t -> t.getId())
                .orElseThrow();
    }

    private ReplyTemplate findOrCreateReplyTemplate(User landlord, Long documentTemplateId) {
        return replyTemplateRepository.findByLandlord_IdOrderByTitleAsc(landlord.getId()).stream()
                .filter(t -> "Демо: заселение с документом".equals(t.getTitle()))
                .findFirst()
                .orElseGet(() -> {
                    ReplyTemplate t = new ReplyTemplate();
                    t.setLandlord(landlord);
                    t.setTitle("Демо: заселение с документом");
                    t.setPlatform(ExternalPlatform.SUTOCHNO);
                    t.setDocumentTemplateId(documentTemplateId);
                    t.setBody("Здравствуйте, {guestFullName}! Заполните паспортные данные и ознакомьтесь с документом: {documentLink}");
                    return replyTemplateRepository.save(t);
                });
    }

    private String ensureGuestFormUrl(User guest) {
        if (guest.getDataCollectionToken() == null
                || guest.getDataCollectionTokenExpiresAt() == null
                || Instant.now().isAfter(guest.getDataCollectionTokenExpiresAt())) {
            guest.setDataCollectionToken(UUID.randomUUID().toString());
            guest.setDataCollectionTokenExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            userRepository.save(guest);
        }
        return "/guest/data/" + guest.getDataCollectionToken();
    }
}
