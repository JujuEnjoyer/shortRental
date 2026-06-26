package com.rental.shortrental.property.infrastructure.repository;

import com.rental.shortrental.property.infrastructure.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    List<Property> findByUser_Id(Long userId);
}
