package com.rental.shortrental.messaging.infrastructure;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.messaging.application.TemplateRenderService;
import com.rental.shortrental.messaging.entity.ReplyTemplate;
import com.rental.shortrental.messaging.repository.ReplyTemplateRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landlord/reply-templates")
public class LandlordReplyTemplateController {

    private final ReplyTemplateRepository templateRepository;
    private final LandlordAccess landlordAccess;
    private final TemplateRenderService renderService;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public LandlordReplyTemplateController(
            ReplyTemplateRepository templateRepository,
            LandlordAccess landlordAccess,
            TemplateRenderService renderService,
            UserRepository userRepository,
            BookingRepository bookingRepository
    ) {
        this.templateRepository = templateRepository;
        this.landlordAccess = landlordAccess;
        this.renderService = renderService;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping
    public List<ReplyTemplate> list(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return templateRepository.findByLandlord_IdOrderByTitleAsc(landlord.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReplyTemplate create(Authentication authentication, @RequestBody CreateTemplateRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        ReplyTemplate t = new ReplyTemplate();
        t.setLandlord(landlord);
        t.setTitle(request.title().trim());
        t.setBody(request.body());
        t.setPlatform(parsePlatform(request.platform()));
        t.setDocumentTemplateId(request.documentTemplateId());
        return templateRepository.save(t);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(authentication);
        ReplyTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!t.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        templateRepository.delete(t);
    }

    @PostMapping("/preview")
    public Map<String, String> preview(Authentication authentication, @RequestBody PreviewTemplateRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        ReplyTemplate t = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!t.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Property property = landlordAccess.requireOwnedProperty(landlord, request.propertyId());
        User guest = userRepository.findById(request.guestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));
        if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a guest");
        }
        if (!bookingRepository.existsByGuestAndLandlord(guest.getId(), landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest is not linked to your bookings");
        }
        renderService.ensureGuestDataToken(guest);
        userRepository.save(guest);
        String text = renderService.render(t.getBody(), landlord, guest, property);
        return Map.of("text", text);
    }

    /**
     * Renders arbitrary template text (e.g. before saving as a template) with placeholders.
     * Requires a guest linked to your bookings so {@code {guestFormLink}} can be issued safely.
     */
    @PostMapping("/render")
    public Map<String, String> renderRaw(Authentication authentication, @RequestBody RenderRawRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        if (request.body() == null || request.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        Property property = landlordAccess.requireOwnedProperty(landlord, request.propertyId());
        User guest = userRepository.findById(request.guestId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));
        if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a guest");
        }
        if (!bookingRepository.existsByGuestAndLandlord(guest.getId(), landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest is not linked to your bookings");
        }
        renderService.ensureGuestDataToken(guest);
        userRepository.save(guest);
        String text = renderService.render(request.body(), landlord, guest, property);
        return Map.of("text", text);
    }

    private static ExternalPlatform parsePlatform(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ExternalPlatform.valueOf(raw.trim().toUpperCase());
    }

    public record CreateTemplateRequest(String title, String body, String platform, Long documentTemplateId) {
    }

    public record PreviewTemplateRequest(Long templateId, Long propertyId, Long guestId) {
    }

    public record RenderRawRequest(String body, Long propertyId, Long guestId) {
    }
}
