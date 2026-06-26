package com.rental.shortrental.messaging.repository;

import com.rental.shortrental.messaging.entity.ReplyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplyTemplateRepository extends JpaRepository<ReplyTemplate, Long> {
    List<ReplyTemplate> findByLandlord_IdOrderByTitleAsc(Long landlordId);
}
