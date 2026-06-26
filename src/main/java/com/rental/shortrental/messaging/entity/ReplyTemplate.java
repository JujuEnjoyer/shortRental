package com.rental.shortrental.messaging.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
@JsonIgnoreProperties({"landlord"})
public class ReplyTemplate {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private User landlord;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 8000)
    private String body;

    @Enumerated(EnumType.STRING)
    private ExternalPlatform platform;

    private Long documentTemplateId;   // which document to create on {documentLink}

    public Long getId() {
        return id;
    }

    public User getLandlord() {
        return landlord;
    }

    public void setLandlord(User landlord) {
        this.landlord = landlord;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public ExternalPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ExternalPlatform platform) {
        this.platform = platform;
    }

    public Long getDocumentTemplateId() {
        return documentTemplateId;
    }

    public void setDocumentTemplateId(Long documentTemplateId) {
        this.documentTemplateId = documentTemplateId;
    }
}
