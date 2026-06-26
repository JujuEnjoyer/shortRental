package com.rental.shortrental.booking.infrastructure.entity;

import com.rental.shortrental.property.infrastructure.entity.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@Entity
public class Calendar {
    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExternalPlatform source;

    @Column(nullable = false)
    private String icaUrl;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne
    private Property property;
}
