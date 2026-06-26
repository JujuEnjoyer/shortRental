package com.rental.shortrental.messaging.entity;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.property.infrastructure.entity.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.OffsetDateTime;

@Entity
public class GuestMessage {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalPlatform platform;

    @Column(nullable = false)
    private String externalConversationId;

    @Column(nullable = false, unique = true)
    private String externalMessageId;

    @Column(nullable = false)
    private String guestDisplayName;

    @Column(length = 4000, nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public ExternalPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(ExternalPlatform platform) {
        this.platform = platform;
    }

    public String getExternalConversationId() {
        return externalConversationId;
    }

    public void setExternalConversationId(String externalConversationId) {
        this.externalConversationId = externalConversationId;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String externalMessageId) {
        this.externalMessageId = externalMessageId;
    }

    public String getGuestDisplayName() {
        return guestDisplayName;
    }

    public void setGuestDisplayName(String guestDisplayName) {
        this.guestDisplayName = guestDisplayName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageDirection getDirection() {
        return direction;
    }

    public void setDirection(MessageDirection direction) {
        this.direction = direction;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
