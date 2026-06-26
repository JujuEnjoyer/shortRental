package com.rental.shortrental.document.application;

import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.entity.DocumentTemplate;
import com.rental.shortrental.document.interfaces.DocumentRepository;
import com.rental.shortrental.document.interfaces.DocumentTemplateRepository;
import com.rental.shortrental.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentLinkServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTemplateRepository templateRepository;

    @Test
    void createDocumentLinkCreatesDraftWithGuestToken() {
        DocumentLinkService service = new DocumentLinkService(
                documentRepository,
                templateRepository,
                "http://localhost:8080/"
        );
        User landlord = user(1L, "owner@example.test", "LANDLORD");
        User guest = user(2L, "guest@example.test", "GUEST");
        DocumentTemplate template = new DocumentTemplate();
        template.setId(7L);
        template.setTitle("Договор аренды");
        template.setDocumentType("CONTRACT");
        template.setSystem(true);
        template.setEditableFieldsConfig("{\"advancePayment\":{\"enabled\":true,\"defaultValue\":5000}}");
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String link = service.createDocumentLink(landlord, guest, template.getId());

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document doc = captor.getValue();
        assertThat(link).startsWith("http://localhost:8080/guest/data/");
        assertThat(doc.getTitle()).isEqualTo("Договор аренды");
        assertThat(doc.getDocumentType()).isEqualTo("CONTRACT");
        assertThat(doc.getStatus()).isEqualTo("DRAFT");
        assertThat(doc.getLandlord()).isSameAs(landlord);
        assertThat(doc.getGuest()).isSameAs(guest);
        assertThat(doc.getTemplate()).isSameAs(template);
        assertThat(doc.getGuestFillToken()).isNotBlank();
        assertThat(doc.getGuestFillExpiresAt()).isAfter(Instant.now());
        assertThat(doc.getEditableJson()).contains("\"advancePayment\":\"5000\"");
    }

    private static User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
