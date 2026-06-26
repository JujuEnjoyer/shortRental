package com.rental.shortrental.privacy;

import com.rental.shortrental.audit.AuditAction;
import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.common.util.FileStorageService;
import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.interfaces.DocumentRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DataRetentionService {

    private final DataRetentionPolicyRepository policyRepository;
    private final BookingRepository bookingRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    public DataRetentionService(
            DataRetentionPolicyRepository policyRepository,
            BookingRepository bookingRepository,
            DocumentRepository documentRepository,
            UserRepository userRepository,
            FileStorageService fileStorageService,
            AuditService auditService
    ) {
        this.policyRepository = policyRepository;
        this.bookingRepository = bookingRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    @Transactional
    public DataRetentionPolicy getOrCreatePolicy(User landlord) {
        return policyRepository.findByLandlordId(landlord.getId())
                .orElseGet(() -> {
                    DataRetentionPolicy policy = new DataRetentionPolicy();
                    policy.setLandlord(landlord);
                    policy.setUpdatedAt(Instant.now());
                    return policyRepository.save(policy);
                });
    }

    @Transactional
    public DataRetentionPolicy updatePolicy(User landlord, UpdateRetentionPolicyRequest request) {
        DataRetentionPolicy policy = getOrCreatePolicy(landlord);
        policy.setPassportDataRetentionDays(clamp(request.passportDataRetentionDays(), 1, 3650));
        policy.setGeneratedPdfRetentionDays(clamp(request.generatedPdfRetentionDays(), 1, 3650));
        policy.setDeletePassportPhotos(request.deletePassportPhotos());
        policy.setAutoDeleteEnabled(request.autoDeleteEnabled());
        policy.setUpdatedAt(Instant.now());
        DataRetentionPolicy saved = policyRepository.save(policy);
        auditService.record(
                landlord,
                landlord,
                AuditAction.RETENTION_POLICY_UPDATED,
                "DATA_RETENTION_POLICY",
                saved.getId(),
                "Обновлена политика хранения данных"
        );
        return saved;
    }

    @Transactional
    public RetentionPurgeResult purgeExpired(User landlord) {
        DataRetentionPolicy policy = getOrCreatePolicy(landlord);
        LocalDate passportCutoff = LocalDate.now().minusDays(policy.getPassportDataRetentionDays());
        Instant pdfCutoff = Instant.now().minusSeconds((long) policy.getGeneratedPdfRetentionDays() * 24 * 60 * 60);

        List<Booking> oldBookings = bookingRepository
                .findByProperty_User_IdAndEndDateBeforeAndGuestIsNotNull(landlord.getId(), passportCutoff);

        Map<Long, User> guestsToClean = new LinkedHashMap<>();
        for (Booking booking : oldBookings) {
            if (booking.getGuest() != null) {
                guestsToClean.put(booking.getGuest().getId(), booking.getGuest());
            }
        }

        int photosDeleted = 0;
        for (User guest : guestsToClean.values()) {
            if (policy.isDeletePassportPhotos()) {
                photosDeleted += deleteIfPresent(guest.getPassportPhoto1());
                photosDeleted += deleteIfPresent(guest.getPassportPhoto2());
                guest.setPassportPhoto1(null);
                guest.setPassportPhoto2(null);
            }
            guest.setPassportSeries(null);
            guest.setPassportNumber(null);
            guest.setPassportIssuedBy(null);
            guest.setPassportIssueDate(null);
            guest.setBirthDate(null);
            guest.setGuestPhone(null);
            guest.setPassportVerified("UNCHECKED");
            guest.setDataCollectionToken(null);
            guest.setDataCollectionTokenExpiresAt(null);
            userRepository.save(guest);
        }

        List<Document> oldDocuments = documentRepository
                .findByLandlordIdAndUpdatedAtBeforeAndPdfFilePathIsNotNull(landlord.getId(), pdfCutoff);
        int pdfDeleted = 0;
        for (Document document : oldDocuments) {
            pdfDeleted += deleteIfPresent(document.getPdfFilePath());
            document.setPdfFilePath(null);
            document.setPdfToken(null);
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
        }

        RetentionPurgeResult result = new RetentionPurgeResult(
                guestsToClean.size(),
                photosDeleted,
                oldDocuments.size(),
                pdfDeleted,
                passportCutoff,
                pdfCutoff
        );

        auditService.record(
                landlord,
                landlord,
                AuditAction.RETENTION_PURGE_RUN,
                "DATA_RETENTION_POLICY",
                policy.getId(),
                "Выполнена ручная очистка данных",
                "guests=" + result.guestsCleaned()
                        + ", photos=" + result.passportPhotosDeleted()
                        + ", documents=" + result.documentsCleaned()
                        + ", pdf=" + result.pdfFilesDeleted()
        );
        return result;
    }

    private int deleteIfPresent(String fileName) {
        if (Objects.isNull(fileName) || fileName.isBlank()) {
            return 0;
        }
        fileStorageService.delete(fileName);
        return 1;
    }

    private static int clamp(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public record UpdateRetentionPolicyRequest(
            Integer passportDataRetentionDays,
            Integer generatedPdfRetentionDays,
            boolean deletePassportPhotos,
            boolean autoDeleteEnabled
    ) {
    }

    public record RetentionPurgeResult(
            int guestsCleaned,
            int passportPhotosDeleted,
            int documentsCleaned,
            int pdfFilesDeleted,
            LocalDate passportCutoffDate,
            Instant pdfCutoffAt
    ) {
    }
}
