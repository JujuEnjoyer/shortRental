package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.messaging.entity.GuestMessage;
import com.rental.shortrental.messaging.entity.MessageDirection;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import com.rental.shortrental.messaging.repository.GuestMessageRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PartnerPlatformInboxService {
    private final MessageSyncService messageSyncService;
    private final GuestMessageRepository messageRepository;
    private final ExternalIntegrationConfigRepository integrationRepository;

    public PartnerPlatformInboxService(
            MessageSyncService messageSyncService,
            GuestMessageRepository messageRepository,
            ExternalIntegrationConfigRepository integrationRepository
    ) {
        this.messageSyncService = messageSyncService;
        this.messageRepository = messageRepository;
        this.integrationRepository = integrationRepository;
    }

    public List<AvitoChatListItem> listAllChats(Long ownerId, ExternalPlatform platform) {
        messageSyncService.syncOwnerMessages(ownerId);
        List<GuestMessage> messages = messageRepository.findByLandlordAndPlatform(ownerId, platform);
        Map<String, GuestMessage> latestByThread = new LinkedHashMap<>();
        for (GuestMessage message : messages) {
            String key = message.getProperty().getId() + "::" + message.getExternalConversationId();
            latestByThread.putIfAbsent(key, message);
        }
        return latestByThread.values().stream()
                .map(m -> new AvitoChatListItem(
                        integrationId(ownerId, platform, m.getProperty().getId()),
                        m.getProperty().getId(),
                        m.getProperty().getName(),
                        m.getExternalConversationId(),
                        platformTitle(platform),
                        m.getContent(),
                        epochSeconds(m),
                        m.getGuestDisplayName(),
                        platform
                ))
                .sorted(Comparator.comparingLong(AvitoChatListItem::updatedAt).reversed())
                .toList();
    }

    public List<AvitoThreadMessageDto> listThreadMessages(
            Long ownerId,
            ExternalPlatform platform,
            long propertyId,
            String chatId
    ) {
        return messageRepository.findThread(ownerId, propertyId, platform, chatId).stream()
                .map(m -> new AvitoThreadMessageDto(
                        m.getExternalMessageId(),
                        m.getDirection() == MessageDirection.INBOUND ? "in" : "out",
                        "text",
                        m.getContent(),
                        0,
                        m.getGuestDisplayName(),
                        epochSeconds(m)
                ))
                .toList();
    }

    private long integrationId(Long ownerId, ExternalPlatform platform, Long propertyId) {
        return integrationRepository.findByPropertyUserId(ownerId).stream()
                .filter(c -> c.getPlatform() == platform)
                .filter(c -> c.getProperty().getId().equals(propertyId))
                .map(c -> c.getId() == null ? 0L : c.getId())
                .findFirst()
                .orElse(0L);
    }

    private static String platformTitle(ExternalPlatform platform) {
        return switch (platform) {
            case SUTOCHNO -> "Суточно.ру";
            case YANDEX_TRAVEL -> "Яндекс Путешествия";
            case OSTROVOK -> "Островок";
            case HOSTAWAY -> "Hostaway";
            case CHANNEX -> "Channex";
            case BOOKING -> "Booking.com";
            case GUESTY -> "Guesty";
            default -> platform.name();
        };
    }

    private static long epochSeconds(GuestMessage message) {
        if (message.getCreatedAt() == null) {
            return 0;
        }
        return message.getCreatedAt().toInstant().atOffset(ZoneOffset.UTC).toEpochSecond();
    }
}
