package com.rental.shortrental.privacy;

import com.rental.shortrental.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

import java.time.Instant;

@Entity
public class DataRetentionPolicy {
    @Id
    @GeneratedValue
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private User landlord;

    @Column(nullable = false)
    private int passportDataRetentionDays = 180;

    @Column(nullable = false)
    private int generatedPdfRetentionDays = 365;

    @Column(nullable = false)
    private boolean deletePassportPhotos = true;

    @Column(nullable = false)
    private boolean autoDeleteEnabled = false;

    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public User getLandlord() {
        return landlord;
    }

    public void setLandlord(User landlord) {
        this.landlord = landlord;
    }

    public int getPassportDataRetentionDays() {
        return passportDataRetentionDays;
    }

    public void setPassportDataRetentionDays(int passportDataRetentionDays) {
        this.passportDataRetentionDays = passportDataRetentionDays;
    }

    public int getGeneratedPdfRetentionDays() {
        return generatedPdfRetentionDays;
    }

    public void setGeneratedPdfRetentionDays(int generatedPdfRetentionDays) {
        this.generatedPdfRetentionDays = generatedPdfRetentionDays;
    }

    public boolean isDeletePassportPhotos() {
        return deletePassportPhotos;
    }

    public void setDeletePassportPhotos(boolean deletePassportPhotos) {
        this.deletePassportPhotos = deletePassportPhotos;
    }

    public boolean isAutoDeleteEnabled() {
        return autoDeleteEnabled;
    }

    public void setAutoDeleteEnabled(boolean autoDeleteEnabled) {
        this.autoDeleteEnabled = autoDeleteEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
