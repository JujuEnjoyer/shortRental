package com.rental.shortrental.web;

import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.application.PdfGenerationService;
import com.rental.shortrental.document.interfaces.DocumentRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.util.Optional;

@Controller
public class GuestDataPageController {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PdfGenerationService pdfGenerationService;

    public GuestDataPageController(UserRepository userRepository,
                                   DocumentRepository documentRepository,
                                   PdfGenerationService pdfGenerationService) {
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.pdfGenerationService = pdfGenerationService;
    }

    @GetMapping("/guest/data/{token}")
    public String guestDataForm(@PathVariable String token, Model model) {
        Optional<Document> docOpt = documentRepository.findByGuestFillToken(token);
        if (docOpt.isPresent()) {
            Document doc = docOpt.get();
            if (doc.getGuestFillExpiresAt() != null && Instant.now().isAfter(doc.getGuestFillExpiresAt())) {
                model.addAttribute("token", token);
                model.addAttribute("error", "Срок ссылки истёк — попросите арендодателя выпустить новую.");
                return "guest-data";
            }

            User landlord = doc.getLandlord();

            model.addAttribute("token", token);
            model.addAttribute("mode", "document");
            model.addAttribute("error", false);
            model.addAttribute("document", doc);
            model.addAttribute("landlord", landlord);
            model.addAttribute("guest", doc.getGuest());

            String bodyHtml = null;
            if (doc.getTemplate() != null) {
                bodyHtml = pdfGenerationService.renderHtml(doc, doc.getTemplate(), landlord, doc.getGuest(), java.util.Map.of(), true);
            }
            model.addAttribute("bodyHtml", bodyHtml);

            return "guest-data";
        }

        return userRepository.findByDataCollectionToken(token)
                .map(u -> {
                    if (u.getDataCollectionTokenExpiresAt() == null
                            || Instant.now().isAfter(u.getDataCollectionTokenExpiresAt())) {
                        model.addAttribute("token", token);
                        model.addAttribute("error", "Срок ссылки истёк — попросите арендодателя выпустить новую.");
                        return "guest-data";
                    }
                    model.addAttribute("token", token);
                    model.addAttribute("mode", "guest");
                    model.addAttribute("error", false);
                    return "guest-data";
                })
                .orElseGet(() -> {
                    model.addAttribute("token", token);
                    model.addAttribute("error", "Ссылка недействительна.");
                    return "guest-data";
                });
    }
}
