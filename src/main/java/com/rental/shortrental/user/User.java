package com.rental.shortrental.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String name;
    private String surname;
    @Column(length = 32)
    private String phone;
    private String role;

    /* —— Landlord document profile, inserted into contracts before guest fills their side —— */
    @Column(length = 16)
    private String landlordPassportSeries;
    @Column(length = 32)
    private String landlordPassportNumber;
    @Column(length = 512)
    private String landlordPassportIssuedBy;
    private LocalDate landlordPassportIssueDate;
    @Column(length = 512)
    private String landlordRegistrationAddress;

    /**
     * After first-time setup wizard; returning users skip onboarding and open /app.
     */
    @Column(nullable = false)
    private boolean onboardingCompleted = false;

    /* —— Guest profile (passport / contacts), filled by guest via secure link —— */
    @Column(length = 16)
    private String passportSeries;
    @Column(length = 32)
    private String passportNumber;
    @Column(length = 512)
    private String passportIssuedBy;
    private LocalDate passportIssueDate;
    private LocalDate birthDate;
    @Column(length = 32)
    private String guestPhone;

    @Column(unique = true, length = 64)
    private String dataCollectionToken;
    private Instant dataCollectionTokenExpiresAt;

    /* —— Passport photos —— */
    @Column(length = 256)
    private String passportPhoto1;
    @Column(length = 256)
    private String passportPhoto2;

    /* —— Verification status: UNCHECKED / VERIFIED / REJECTED —— */
    private String passportVerified = "UNCHECKED";

}
