package com.rental.shortrental.document.interfaces;

import com.rental.shortrental.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByLandlordIdOrderByCreatedAtDesc(Long landlordId);
    List<Document> findByLandlordIdAndStatusOrderByCreatedAtDesc(Long landlordId, String status);
    List<Document> findByLandlordIdAndUpdatedAtBeforeAndPdfFilePathIsNotNull(Long landlordId, Instant updatedAt);
    Optional<Document> findByPdfToken(String pdfToken);
    Optional<Document> findByGuestFillToken(String guestFillToken);
}
