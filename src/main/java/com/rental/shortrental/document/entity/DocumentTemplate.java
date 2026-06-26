package com.rental.shortrental.document.entity;

import com.rental.shortrental.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "document_templates")
public class DocumentTemplate {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private User landlord;

    private String title;

    private String documentType;       // CONTRACT, ACT, etc.

    @Column(length = 64000)
    private String bodyHtml;           // full HTML content (not filename)

    private String docxFilePath;       // path to uploaded .docx in storage (null for system templates)

    private boolean system;            // true for built-in templates (Lombok generates setSystem/isSystem)

    @Column(length = 4000)
    private String editableFieldsConfig; // JSON: {"advancePayment":{"enabled":false,"defaultValue":5000},"additionalTerms":{"enabled":false}}
}
