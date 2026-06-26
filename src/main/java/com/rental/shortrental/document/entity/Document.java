package com.rental.shortrental.document.entity;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue
    private Long id;

    private String title;
    private String documentType;       // CONTRACT, ADDITIONAL_AGREEMENT, ACT
    private String status;             // DRAFT, AWAITING_APPROVAL, FINALIZED, REJECTED

    @ManyToOne
    private User guest;

    @ManyToOne
    private User landlord;

    @ManyToOne
    private Property property;

    @ManyToOne
    private Booking booking;

    @ManyToOne
    private DocumentTemplate template;

    @Column(length = 8000)
    private String editableJson;       // JSON with advancePayment, additionalTerms, etc.

    private String pdfFilePath;        // relative path in storage

    @Column(unique = true, length = 64)
    private String pdfToken;           // for public guest access to download PDF

    @Column(unique = true, length = 64)
    private String guestFillToken;     // for guest to fill data via link

    private Instant guestFillExpiresAt;

    @Column(length = 8000)
    private String renderedHtml;       // cached rendered HTML for preview/editing

    private Instant guestDataFilledAt;

    private Instant landlordReviewedAt;

    private Instant createdAt;
    private Instant updatedAt;
}
