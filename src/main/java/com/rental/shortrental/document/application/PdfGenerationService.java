package com.rental.shortrental.document.application;

import com.rental.shortrental.common.util.FileStorageService;
import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.entity.DocumentTemplate;
import com.rental.shortrental.user.User;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

@Service
public class PdfGenerationService {

    private final TemplateEngine stringEngine;
    private final FileStorageService fileStorageService;

    public PdfGenerationService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
        this.stringEngine = new SpringTemplateEngine();
        this.stringEngine.setTemplateResolver(new StringTemplateResolver());
    }

    public String generate(Document doc, DocumentTemplate template, User landlord, User guest,
                           Map<String, Object> extraData) {
        String html = renderHtml(doc, template, landlord, guest, extraData, false);

        try {
            org.xhtmlrenderer.pdf.ITextRenderer renderer = new org.xhtmlrenderer.pdf.ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            renderer.createPDF(baos);
            byte[] pdfBytes = baos.toByteArray();

            String token = UUID.randomUUID().toString();
            String filename = fileStorageService.storeBytes(pdfBytes, "doc_" + doc.getId(), ".pdf");

            doc.setPdfToken(token);
            doc.setPdfFilePath(filename);
            doc.setRenderedHtml(html);
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    public String renderHtml(Document doc, DocumentTemplate template, User landlord, User guest,
                             Map<String, Object> extraData, boolean blankGuestData) {
        Context ctx = new Context();
        ctx.setVariable("landlordName", joinName(landlord));
        ctx.setVariable("landlordPhone", nullToEmpty(landlord.getPhone()));
        ctx.setVariable("landlordEmail", nullToEmpty(landlord.getEmail()));
        ctx.setVariable("landlordPassportSeries", nullToEmpty(landlord.getLandlordPassportSeries()));
        ctx.setVariable("landlordPassportNumber", nullToEmpty(landlord.getLandlordPassportNumber()));
        ctx.setVariable("landlordPassportIssuedBy", nullToEmpty(landlord.getLandlordPassportIssuedBy()));
        ctx.setVariable("landlordPassportIssueDate", landlord.getLandlordPassportIssueDate() != null ? landlord.getLandlordPassportIssueDate().toString() : "");
        ctx.setVariable("landlordRegistrationAddress", nullToEmpty(landlord.getLandlordRegistrationAddress()));
        ctx.setVariable("guestName", blankGuestData ? "" : joinName(guest));
        ctx.setVariable("passportSeries", blankGuestData ? "" : nullToEmpty(guest.getPassportSeries()));
        ctx.setVariable("passportNumber", blankGuestData ? "" : nullToEmpty(guest.getPassportNumber()));
        ctx.setVariable("passportIssuedBy", blankGuestData ? "" : nullToEmpty(guest.getPassportIssuedBy()));
        ctx.setVariable("passportIssueDate", blankGuestData || guest.getPassportIssueDate() == null ? "" : guest.getPassportIssueDate().toString());
        ctx.setVariable("birthDate", blankGuestData || guest.getBirthDate() == null ? "" : guest.getBirthDate().toString());
        ctx.setVariable("guestPhone", blankGuestData ? "" : nullToEmpty(guest.getGuestPhone()));
        ctx.setVariable("guestEmail", blankGuestData ? "" : nullToEmpty(guest.getEmail()));
        ctx.setVariable("propertyName", doc.getProperty() != null ? nullToEmpty(doc.getProperty().getName()) : "");
        ctx.setVariable("propertyAddress", doc.getProperty() != null ? nullToEmpty(doc.getProperty().getAddress()) : "");
        ctx.setVariable("startDate", doc.getBooking() != null && doc.getBooking().getStartDate() != null ? doc.getBooking().getStartDate().toString() : "");
        ctx.setVariable("endDate", doc.getBooking() != null && doc.getBooking().getEndDate() != null ? doc.getBooking().getEndDate().toString() : "");
        ctx.setVariable("advancePayment", "");
        ctx.setVariable("additionalTerms", "");

        if (extraData != null) {
            extraData.forEach(ctx::setVariable);
        }

        return stringEngine.process(template.getBodyHtml(), ctx);
    }

    private static String joinName(User u) {
        String n = u.getName() == null ? "" : u.getName();
        String s = u.getSurname() == null ? "" : u.getSurname();
        return (n + " " + s).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
