package com.rental.shortrental.analytics;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.document.entity.Document;
import com.rental.shortrental.document.interfaces.DocumentRepository;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.MessageDirection;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import com.rental.shortrental.messaging.repository.GuestMessageRepository;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final PropertyRepository propertyRepository;
    private final BookingRepository bookingRepository;
    private final GuestMessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final ExternalIntegrationConfigRepository integrationRepository;

    public AnalyticsService(
            PropertyRepository propertyRepository,
            BookingRepository bookingRepository,
            GuestMessageRepository messageRepository,
            DocumentRepository documentRepository,
            ExternalIntegrationConfigRepository integrationRepository
    ) {
        this.propertyRepository = propertyRepository;
        this.bookingRepository = bookingRepository;
        this.messageRepository = messageRepository;
        this.documentRepository = documentRepository;
        this.integrationRepository = integrationRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummary buildSummary(User landlord) {
        Long landlordId = landlord.getId();
        List<Booking> bookings = bookingRepository.findByProperty_User_Id(landlordId);
        var messages = messageRepository.findByPropertyUserIdOrderByCreatedAtDesc(landlordId);
        List<Document> documents = documentRepository.findByLandlordIdOrderByCreatedAtDesc(landlordId);
        List<ExternalIntegrationConfig> integrations = integrationRepository.findByPropertyUserId(landlordId);

        LocalDate today = LocalDate.now();
        LocalDate periodEnd = today.plusDays(30);
        long upcomingBookings = bookings.stream()
                .filter(b -> b.getEndDate() != null && !b.getEndDate().isBefore(today))
                .count();
        long occupiedDaysNext30 = bookings.stream()
                .mapToLong(b -> overlapDays(b, today, periodEnd))
                .sum();

        Map<String, Long> bookingsBySource = bookings.stream()
                .filter(b -> b.getSource() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getSource().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<String, Long> messagesByPlatform = messages.stream()
                .filter(m -> m.getPlatform() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getPlatform().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<String, Long> documentsByStatus = documents.stream()
                .filter(d -> d.getStatus() != null)
                .collect(Collectors.groupingBy(
                        Document::getStatus,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<String, Long> integrationsByPlatform = integrations.stream()
                .filter(i -> i.getPlatform() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getPlatform().name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        long inboundMessages = messages.stream()
                .filter(m -> m.getDirection() == MessageDirection.INBOUND)
                .count();
        long outboundMessages = messages.stream()
                .filter(m -> m.getDirection() == MessageDirection.OUTBOUND)
                .count();

        boolean avitoConnected = integrations.stream().anyMatch(i ->
                i.getPlatform() == ExternalPlatform.AVITO
                        && i.isEnabled()
                        && hasText(i.getClientId())
                        && hasText(i.getClientSecret())
                        && hasText(i.getPlatformUserId())
        );

        return new AnalyticsSummary(
                propertyRepository.findByUser_Id(landlordId).size(),
                bookings.size(),
                upcomingBookings,
                occupiedDaysNext30,
                messages.size(),
                inboundMessages,
                outboundMessages,
                documents.size(),
                documentsByStatus.getOrDefault("AWAITING_APPROVAL", 0L),
                documentsByStatus.getOrDefault("FINALIZED", 0L),
                integrations.size(),
                avitoConnected,
                bookingsBySource,
                messagesByPlatform,
                documentsByStatus,
                integrationsByPlatform
        );
    }

    private static long overlapDays(Booking booking, LocalDate periodStart, LocalDate periodEnd) {
        if (booking.getStartDate() == null || booking.getEndDate() == null) {
            return 0;
        }
        LocalDate start = max(booking.getStartDate(), periodStart);
        LocalDate end = min(booking.getEndDate(), periodEnd);
        if (end.isBefore(start)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return Objects.requireNonNull(a).isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return Objects.requireNonNull(a).isBefore(b) ? a : b;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record AnalyticsSummary(
            long propertyCount,
            long bookingCount,
            long upcomingBookingCount,
            long occupiedDaysNext30,
            long messageCount,
            long inboundMessageCount,
            long outboundMessageCount,
            long documentCount,
            long awaitingDocumentCount,
            long finalizedDocumentCount,
            long integrationCount,
            boolean avitoOpenApiConnected,
            Map<String, Long> bookingsBySource,
            Map<String, Long> messagesByPlatform,
            Map<String, Long> documentsByStatus,
            Map<String, Long> integrationsByPlatform
    ) {
    }
}
