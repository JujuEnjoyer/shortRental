package com.rental.shortrental.document.interfaces;

import com.rental.shortrental.document.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    List<DocumentTemplate> findByLandlordIdOrSystemTrueOrderByTitle(Long landlordId);
    List<DocumentTemplate> findByLandlordId(Long landlordId);
    List<DocumentTemplate> findBySystemTrue();
    List<DocumentTemplate> findByLandlordIdAndDocumentType(Long landlordId, String documentType);
    List<DocumentTemplate> findBySystemTrueAndDocumentType(String documentType);
}
