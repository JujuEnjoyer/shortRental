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

@Entity
public class ExternalIntegrationConfig {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private Property property;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IntegrationMode mode;

    @Column(length = 2000)
    private String inboundEndpoint;

    @Column(length = 2000)
    private String outboundEndpoint;

    @Column(length = 2000)
    private String authToken;

    /**
     * Platform-specific account id. For Avito: numeric {@code user_id} in Messenger API paths (seller / company account).
     */
    @Column(length = 64)
    private String platformUserId;

    /**
     * Avito OAuth client_id (per-user credentials).
     */
    @Column(length = 128)
    private String clientId;

    /**
     * Avito OAuth client_secret (per-user credentials).
     */
    @Column(length = 256)
    private String clientSecret;

    @Column(nullable = false)
    private boolean enabled = true;

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

    public IntegrationMode getMode() {
        return mode;
    }

    public void setMode(IntegrationMode mode) {
        this.mode = mode;
    }

    public String getInboundEndpoint() {
        return inboundEndpoint;
    }

    public void setInboundEndpoint(String inboundEndpoint) {
        this.inboundEndpoint = inboundEndpoint;
    }

    public String getOutboundEndpoint() {
        return outboundEndpoint;
    }

    public void setOutboundEndpoint(String outboundEndpoint) {
        this.outboundEndpoint = outboundEndpoint;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
}
