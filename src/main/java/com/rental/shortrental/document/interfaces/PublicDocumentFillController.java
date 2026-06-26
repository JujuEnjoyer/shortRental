package com.rental.shortrental.document.interfaces;

import com.rental.shortrental.audit.AuditAction;
import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.common.util.FileStorageService;
import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.user.GuestDataPublicController;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/public/documents")
public class PublicDocumentFillController {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public PublicDocumentFillController(DocumentRepository documentRepository,
                                        UserRepository userRepository,
                                        FileStorageService fileStorageService,
                                        AuditService auditService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @PostMapping("/{token}/fill")
    @Transactional
    public Map<String, Object> fillDocument(@PathVariable String token, @RequestBody GuestDataPublicController.GuestDataSubmission body) {
        Document doc = documentRepository.findByGuestFillToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid link"));
        if (doc.getGuestFillExpiresAt() != null && Instant.now().isAfter(doc.getGuestFillExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Срок ссылки истёк — попросите арендодателя выпустить новую.");
        }

        User guest = doc.getGuest();
        guest.setPassportSeries(trimToNull(body.passportSeries()));
        guest.setPassportNumber(trimToNull(body.passportNumber()));
        guest.setPassportIssuedBy(trimToNull(body.passportIssuedBy()));
        guest.setPassportIssueDate(body.passportIssueDate());
        guest.setBirthDate(body.birthDate());
        guest.setGuestPhone(trimToNull(body.guestPhone()));
        userRepository.save(guest);

        doc.setStatus("AWAITING_APPROVAL");
        doc.setGuestDataFilledAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
        auditService.record(
                doc.getLandlord(),
                guest,
                AuditAction.GUEST_DATA_SUBMITTED,
                "DOCUMENT",
                doc.getId(),
                "Гость отправил данные по ссылке документа"
        );

        return Map.of("status", "ok", "message", "Данные отправлены на проверку арендодателю.");
    }

    @PostMapping("/{token}/photos")
    @Transactional
    public Map<String, String> uploadPhotos(
            @PathVariable String token,
            @RequestParam(value = "photo1", required = false) MultipartFile photo1,
            @RequestParam(value = "photo2", required = false) MultipartFile photo2
    ) {
        Document doc = documentRepository.findByGuestFillToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid link"));
        if (doc.getGuestFillExpiresAt() != null && Instant.now().isAfter(doc.getGuestFillExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Срок ссылки истёк");
        }
        User guest = doc.getGuest();
        fileStorageService.delete(guest.getPassportPhoto1());
        fileStorageService.delete(guest.getPassportPhoto2());
        if (photo1 != null && !photo1.isEmpty()) {
            guest.setPassportPhoto1(fileStorageService.store(photo1, "pp1"));
        }
        if (photo2 != null && !photo2.isEmpty()) {
            guest.setPassportPhoto2(fileStorageService.store(photo2, "pp2"));
        }
        userRepository.save(guest);
        auditService.record(
                doc.getLandlord(),
                guest,
                AuditAction.GUEST_PHOTOS_UPLOADED,
                "DOCUMENT",
                doc.getId(),
                "Гость загрузил фото документа"
        );
        return Map.of("status", "ok");
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
