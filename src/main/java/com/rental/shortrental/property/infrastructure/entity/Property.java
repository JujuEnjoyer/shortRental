package com.rental.shortrental.property.infrastructure.entity;

import com.rental.shortrental.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Property {
    @Id
    @GeneratedValue
    private Long id;

    private String address;

    private String name;

    @ManyToOne
    private User user;

}
