package com.rental.shortrental.document.interfaces;

import com.rental.shortrental.audit.AuditAction;
import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.common.util.FileStorageService;
import com.rental.shortrental.document.application.DocxConversionService;
import com.rental.shortrental.document.application.PdfGenerationService;
import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.entity.DocumentTemplate;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/landlord/documents")
public class LandlordDocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentTemplateRepository documentTemplateRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final LandlordAccess landlordAccess;
    private final PdfGenerationService pdfGenerationService;
    private final FileStorageService fileStorageService;
    private final DocxConversionService docxConversionService;
    private final AuditService auditService;

    public LandlordDocumentController(
            DocumentRepository documentRepository,
            DocumentTemplateRepository documentTemplateRepository,
            UserRepository userRepository,
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            LandlordAccess landlordAccess,
            PdfGenerationService pdfGenerationService,
            FileStorageService fileStorageService,
            DocxConversionService docxConversionService,
            AuditService auditService
    ) {
        this.documentRepository = documentRepository;
        this.documentTemplateRepository = documentTemplateRepository;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.landlordAccess = landlordAccess;
        this.pdfGenerationService = pdfGenerationService;
        this.fileStorageService = fileStorageService;
        this.docxConversionService = docxConversionService;
        this.auditService = auditService;
    }

    /* ========== Documents ========== */

    @GetMapping
    public List<DocumentListResponse> list(Authentication auth,
                                           @RequestParam(required = false) String status) {
        User landlord = landlordAccess.requireLandlord(auth);
        List<Document> docs;
        if (status != null && !status.isBlank()) {
            docs = documentRepository.findByLandlordIdAndStatusOrderByCreatedAtDesc(landlord.getId(), status);
        } else {
            docs = documentRepository.findByLandlordIdOrderByCreatedAtDesc(landlord.getId());
        }
        return docs.stream().map(this::toListResponse).toList();
    }

    @GetMapping("/{id}")
    public Document get(Authentication auth, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(auth);
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return doc;
    }

    @PostMapping("/create-from-chat")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> createFromChat(Authentication auth, @RequestBody Map<String, Object> body) {
        User landlord = landlordAccess.requireLandlord(auth);

        Long guestId = body.get("guestId") != null ? Long.valueOf(body.get("guestId").toString()) : null;
        Long templateId = body.get("templateId") != null ? Long.valueOf(body.get("templateId").toString()) : null;
        Long propertyId = body.get("propertyId") != null ? Long.valueOf(body.get("propertyId").toString()) : null;

        if (guestId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guestId is required");
        }

        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));

        DocumentTemplate template = resolveTemplate(landlord, templateId);

        String token = UUID.randomUUID().toString();

        Document doc = new Document();
        doc.setTitle(template != null ? template.getTitle() : "Документ");
        doc.setDocumentType(template != null ? template.getDocumentType() : "CONTRACT");
        doc.setStatus("DRAFT");
        doc.setGuest(guest);
        doc.setLandlord(landlord);
        doc.setTemplate(template);

        if (propertyId != null) {
            Property property = landlordAccess.requireOwnedProperty(landlord, propertyId);
            doc.setProperty(property);
        }

        doc.setGuestFillToken(token);
        doc.setGuestFillExpiresAt(Instant.now().plus(java.time.Duration.ofDays(14)));
        doc.setEditableJson("{}");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        documentRepository.save(doc);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_CREATED,
                "DOCUMENT",
                doc.getId(),
                "Создан документ и гостевая ссылка"
        );

        String link = "/guest/data/" + token;
        return Map.of("link", link, "token", token);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public Document approve(Authentication auth, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(auth);
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"AWAITING_APPROVAL".equals(doc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Документ уже обработан (статус: " + doc.getStatus() + ")");
        }

        doc.setStatus("FINALIZED");
        doc.setLandlordReviewedAt(Instant.now());

        // Generate PDF
        if (doc.getTemplate() != null) {
            User guest = doc.getGuest();
            Map<String, Object> extraData = parseJsonMap(doc.getEditableJson());
            pdfGenerationService.generate(doc, doc.getTemplate(), landlord, guest, extraData);
        }

        doc.setUpdatedAt(Instant.now());
        Document saved = documentRepository.save(doc);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_APPROVED,
                "DOCUMENT",
                saved.getId(),
                "Документ подтвержден и финализирован"
        );
        return saved;
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public Document reject(Authentication auth, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(auth);
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"AWAITING_APPROVAL".equals(doc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Документ уже обработан (статус: " + doc.getStatus() + ")");
        }

        doc.setStatus("REJECTED");
        doc.setLandlordReviewedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        Document saved = documentRepository.save(doc);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_REJECTED,
                "DOCUMENT",
                saved.getId(),
                "Документ отклонен"
        );
        return saved;
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication auth, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(auth);
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (doc.getPdfFilePath() != null) {
            fileStorageService.delete(doc.getPdfFilePath());
        }
        documentRepository.delete(doc);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_DELETED,
                "DOCUMENT",
                id,
                "Документ удален"
        );
    }

    /* ========== Document templates ========== */

    @GetMapping("/templates")
    public List<DocumentTemplate> listTemplates(Authentication auth) {
        User landlord = landlordAccess.requireLandlord(auth);
        return documentTemplateRepository.findByLandlordIdOrSystemTrueOrderByTitle(landlord.getId());
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTemplate createTemplate(Authentication auth, @RequestBody CreateTemplateRequest request) {
        User landlord = landlordAccess.requireLandlord(auth);
        DocumentTemplate tpl = new DocumentTemplate();
        tpl.setLandlord(landlord);
        tpl.setTitle(request.title());
        tpl.setDocumentType(request.documentType());
        tpl.setBodyHtml(request.bodyHtml());
        tpl.setSystem(false);
        tpl.setEditableFieldsConfig(defaultEditableFieldsConfig());
        DocumentTemplate saved = documentTemplateRepository.save(tpl);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_TEMPLATE_CREATED,
                "DOCUMENT_TEMPLATE",
                saved.getId(),
                "Создан шаблон документа"
        );
        return saved;
    }

    @PostMapping("/templates/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTemplate uploadTemplate(Authentication auth,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam("title") String title,
                                           @RequestParam("documentType") String documentType) {
        User landlord = landlordAccess.requireLandlord(auth);

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл не выбран");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Только .docx файлы");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл больше 10MB");
        }

        try {
            String html = docxConversionService.convertToHtml(file);
            String storedPath = fileStorageService.store(file, "template_" + landlord.getId());

            DocumentTemplate tpl = new DocumentTemplate();
            tpl.setLandlord(landlord);
            tpl.setTitle(title);
            tpl.setDocumentType(documentType);
            tpl.setBodyHtml(html);
            tpl.setDocxFilePath(storedPath);
            tpl.setSystem(false);
            tpl.setEditableFieldsConfig("{\"advancePayment\":{\"enabled\":false,\"defaultValue\":5000},\"additionalTerms\":{\"enabled\":false}}");
            DocumentTemplate saved = documentTemplateRepository.save(tpl);
            auditService.record(
                    landlord,
                    landlord,
                    AuditAction.DOCUMENT_TEMPLATE_CREATED,
                    "DOCUMENT_TEMPLATE",
                    saved.getId(),
                    "Загружен DOCX-шаблон документа"
            );
            return saved;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ошибка конвертации .docx: " + e.getMessage());
        }
    }

    @PutMapping("/templates/{id}")
    public DocumentTemplate updateTemplate(Authentication auth, @PathVariable Long id,
                                           @RequestBody UpdateTemplateRequest request) {
        User landlord = landlordAccess.requireLandlord(auth);
        DocumentTemplate tpl = documentTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (tpl.getLandlord() == null || !tpl.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (request.title() != null) tpl.setTitle(request.title());
        if (request.documentType() != null) tpl.setDocumentType(request.documentType());
        if (request.bodyHtml() != null) tpl.setBodyHtml(request.bodyHtml());
        if (request.editableFieldsConfig() != null) tpl.setEditableFieldsConfig(request.editableFieldsConfig());
        DocumentTemplate saved = documentTemplateRepository.save(tpl);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_TEMPLATE_UPDATED,
                "DOCUMENT_TEMPLATE",
                saved.getId(),
                "Обновлен шаблон документа"
        );
        return saved;
    }

    @PostMapping("/templates/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTemplate copyTemplate(Authentication auth, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(auth);
        DocumentTemplate source = resolveTemplate(landlord, id);
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        DocumentTemplate copy = new DocumentTemplate();
        copy.setLandlord(landlord);
        copy.setTitle(source.getTitle() + " — моя версия");
        copy.setDocumentType(source.getDocumentType());
        copy.setBodyHtml(source.getBodyHtml());
        copy.setEditableFieldsConfig(source.getEditableFieldsConfig());
        copy.setSystem(false);
        DocumentTemplate saved = documentTemplateRepository.save(copy);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_TEMPLATE_CREATED,
                "DOCUMENT_TEMPLATE",
                saved.getId(),
                "Скопирован шаблон документа"
        );
        return saved;
    }

    @DeleteMapping("/templates/{id}")
    public void deleteTemplate(Authentication auth, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(auth);
        DocumentTemplate tpl = documentTemplateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (tpl.getLandlord() == null || !tpl.getLandlord().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        documentTemplateRepository.delete(tpl);
        auditService.record(
                landlord,
                landlord,
                AuditAction.DOCUMENT_TEMPLATE_DELETED,
                "DOCUMENT_TEMPLATE",
                id,
                "Удален шаблон документа"
        );
    }

    /* ========== Helpers ========== */

    private DocumentTemplate resolveTemplate(User landlord, Long templateId) {
        if (templateId != null) {
            DocumentTemplate template = documentTemplateRepository.findById(templateId).orElse(null);
            if (template == null) {
                return null;
            }
            if (template.isSystem()) {
                return template;
            }
            if (template.getLandlord() != null && template.getLandlord().getId().equals(landlord.getId())) {
                return template;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Template belongs to another landlord");
        }
        List<DocumentTemplate> templates = documentTemplateRepository
                .findByLandlordIdOrSystemTrueOrderByTitle(landlord.getId());
        return templates.isEmpty() ? null : templates.get(0);
    }

    private static String defaultEditableFieldsConfig() {
        return "{\"advancePayment\":{\"enabled\":false,\"defaultValue\":5000},\"additionalTerms\":{\"enabled\":true,\"defaultValue\":\"\"}}";
    }

    private DocumentListResponse toListResponse(Document d) {
        return new DocumentListResponse(
                d.getId(),
                d.getTitle(),
                d.getDocumentType(),
                d.getStatus(),
                d.getGuest() != null ? d.getGuest().getName() + " " + d.getGuest().getSurname() : null,
                d.getPdfFilePath(),
                d.getPdfToken(),
                d.getGuestFillToken(),
                d.getGuestDataFilledAt(),
                d.getLandlordReviewedAt()
        );
    }

    private Map<String, Object> parseJsonMap(String json) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;
        try {
            Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|[0-9]+(?:\\.[0-9]+)?|true|false|null)");
            Matcher matcher = pattern.matcher(json);
            while (matcher.find()) {
                String key = matcher.group(1);
                String value = matcher.group(2);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    result.put(key, value.substring(1, value.length() - 1));
                } else if ("true".equals(value)) {
                    result.put(key, true);
                } else if ("false".equals(value)) {
                    result.put(key, false);
                } else if ("null".equals(value)) {
                    result.put(key, null);
                } else if (value.contains(".")) {
                    result.put(key, Double.parseDouble(value));
                } else {
                    result.put(key, Long.parseLong(value));
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public record CreateTemplateRequest(String title, String documentType, String bodyHtml) {}

    public record UpdateTemplateRequest(String title, String documentType, String bodyHtml, String editableFieldsConfig) {}

    public record DocumentListResponse(Long id, String title, String documentType, String status,
                                       String guestName, String pdfFilePath, String pdfToken,
                                       String guestFillToken, Instant guestDataFilledAt,
                                       Instant landlordReviewedAt) {}
}
