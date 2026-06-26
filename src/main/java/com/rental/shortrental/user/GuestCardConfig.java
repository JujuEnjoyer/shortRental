package com.rental.shortrental.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "guest_card_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = "landlord_id"))
public class GuestCardConfig {

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne
    private User landlord;

    private boolean enabled = true;

    @Column(length = 4000)
    private String fieldsConfigJson;

    @Column(length = 8000)
    private String customFieldsJson;
}
