package com.rental.shortrental.messaging.application;

import com.rental.shortrental.document.application.DocumentLinkService;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class TemplateRenderService {

    private final String publicBaseUrl;
    private final DocumentLinkService documentLinkService;

    public TemplateRenderService(@Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl,
                                 DocumentLinkService documentLinkService) {
        this.publicBaseUrl = publicBaseUrl.replaceAll("/$", "");
        this.documentLinkService = documentLinkService;
    }

    public String ensureGuestDataToken(User guest) {
        Instant now = Instant.now();
        if (guest.getDataCollectionToken() == null
                || guest.getDataCollectionTokenExpiresAt() == null
                || now.isAfter(guest.getDataCollectionTokenExpiresAt())) {
            guest.setDataCollectionToken(UUID.randomUUID().toString());
            guest.setDataCollectionTokenExpiresAt(now.plus(14, ChronoUnit.DAYS));
        }
        return guest.getDataCollectionToken();
    }

    public String guestFormUrl(User guest) {
        String token = guest.getDataCollectionToken();
        if (token == null || token.isBlank()) {
            return "";
        }
        return publicBaseUrl + "/guest/data/" + token;
    }

    public String render(
            String body,
            User landlord,
            User guest,
            Property property,
            Long documentTemplateId
    ) {
        if (body == null) {
            return "";
        }
        String link = "";
        if (guest != null) {
            link = guestFormUrl(guest);
        }
        String landlordName = joinName(landlord);
        String guestName = guest == null ? "" : joinName(guest);
        String propertyName = property == null ? "" : nullToEmpty(property.getName());
        String propertyAddress = property == null ? "" : nullToEmpty(property.getAddress());
        String guestEmail = guest == null ? "" : nullToEmpty(guest.getEmail());

        String result = body
                .replace("{guestFormLink}", link)
                .replace("{guestFormUrl}", link)
                .replace("{landlordName}", landlordName)
                .replace("{guestFullName}", guestName)
                .replace("{guestEmail}", guestEmail)
                .replace("{propertyName}", propertyName)
                .replace("{propertyAddress}", propertyAddress);

        // Handle {documentLink} - create a document and replace with the link
        if (result.contains("{documentLink}") && guest != null && landlord != null) {
            String docLink = documentLinkService.createDocumentLink(landlord, guest, documentTemplateId);
            result = result.replace("{documentLink}", docLink);
        }

        return result;
    }

    public String render(
            String body,
            User landlord,
            User guest,
            Property property
    ) {
        return render(body, landlord, guest, property, null);
    }

    private static String joinName(User u) {
        String n = nullToEmpty(u.getName());
        String s = nullToEmpty(u.getSurname());
        return (n + " " + s).trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
