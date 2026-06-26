package com.rental.shortrental.messaging.application;

import com.rental.shortrental.document.application.DocumentLinkService;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateRenderServiceTest {

    private final DocumentLinkService documentLinkService = mock(DocumentLinkService.class);
    private final TemplateRenderService service = new TemplateRenderService(
            "http://localhost:8080/",
            documentLinkService
    );

    @Test
    void renderSubstitutesGuestPropertyAndDocumentLinks() {
        User landlord = user(1L, "owner@example.test", "Иван", "Иванов", "LANDLORD");
        User guest = user(2L, "guest@example.test", "Анна", "Петрова", "GUEST");
        guest.setDataCollectionToken("guest-token");
        guest.setDataCollectionTokenExpiresAt(Instant.now().plusSeconds(3600));
        Property property = new Property();
        property.setName("Квартира у парка");
        property.setAddress("Москва, Тверская, 1");
        when(documentLinkService.createDocumentLink(landlord, guest, 42L))
                .thenReturn("http://localhost:8080/guest/data/document-token");

        String rendered = service.render(
                "Здравствуйте, {guestFullName}. Объект: {propertyName}, {propertyAddress}. Анкета: {guestFormLink}. Документ: {documentLink}",
                landlord,
                guest,
                property,
                42L
        );

        assertThat(rendered)
                .contains("Анна Петрова")
                .contains("Квартира у парка")
                .contains("Москва, Тверская, 1")
                .contains("http://localhost:8080/guest/data/guest-token")
                .contains("http://localhost:8080/guest/data/document-token");
        verify(documentLinkService).createDocumentLink(landlord, guest, 42L);
    }

    @Test
    void ensureGuestDataTokenCreatesTokenWhenMissing() {
        User guest = user(2L, "guest@example.test", "Анна", "Петрова", "GUEST");

        String token = service.ensureGuestDataToken(guest);

        assertThat(token).isNotBlank();
        assertThat(guest.getDataCollectionToken()).isEqualTo(token);
        assertThat(guest.getDataCollectionTokenExpiresAt()).isAfter(Instant.now());
    }

    private static User user(Long id, String email, String name, String surname, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setSurname(surname);
        user.setRole(role);
        return user;
    }
}
