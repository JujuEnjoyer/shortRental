package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.messaging.entity.DeliveryStatus;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.GuestMessage;
import com.rental.shortrental.messaging.entity.MessageDirection;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import com.rental.shortrental.messaging.repository.GuestMessageRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class MessageSyncService {
    private final ExternalIntegrationConfigRepository integrationRepository;
    private final GuestMessageRepository messageRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final TemplateRenderService templateRenderService;
    private final MessagingPortRegistry portRegistry;

    public MessageSyncService(
            ExternalIntegrationConfigRepository integrationRepository,
            GuestMessageRepository messageRepository,
            PropertyRepository propertyRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            TemplateRenderService templateRenderService,
            MessagingPortRegistry portRegistry
    ) {
        this.integrationRepository = integrationRepository;
        this.messageRepository = messageRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.templateRenderService = templateRenderService;
        this.portRegistry = portRegistry;
    }

    public List<GuestMessage> getOwnerMessages(Long ownerId) {
        return messageRepository.findByPropertyUserIdOrderByCreatedAtDesc(ownerId);
    }

    public List<GuestMessage> getOwnerMessages(Long ownerId, ExternalPlatform platform) {
        if (platform == null) {
            return getOwnerMessages(ownerId);
        }
        return messageRepository.findByLandlordAndPlatform(ownerId, platform);
    }

    @Scheduled(fixedDelayString = "${app.messaging.sync-delay-ms:90000}")
    public void scheduledSync() {
        for (ExternalIntegrationConfig config : integrationRepository.findByEnabledTrue()) {
            syncIntegration(config);
        }
    }

    public int syncOwnerMessages(Long ownerId) {
        int total = 0;
        for (ExternalIntegrationConfig config : integrationRepository.findByPropertyUserId(ownerId)) {
            if (!config.isEnabled()) {
                continue;
            }
            total += syncIntegration(config);
        }
        return total;
    }

    public GuestMessage reply(Long ownerId, ReplyMessageRequest request) {
        ExternalIntegrationConfig config = integrationRepository.findByPropertyUserId(ownerId).stream()
                .filter(c -> c.getProperty().getId().equals(request.propertyId()))
                .filter(c -> c.getPlatform() == request.platform())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration config not found"));

        Property property = propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));

        String raw = request.content() == null ? "" : request.content();

        User landlord = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User guest = null;
        if (request.guestId() != null) {
            guest = userRepository.findById(request.guestId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found"));
            if (!"GUEST".equalsIgnoreCase(guest.getRole())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный пользователь не гость");
            }
            if (!guestLinkedToProperty(ownerId, request.propertyId(), guest.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Гость не привязан к брони по этому объекту");
            }
        }

        if ((request.documentTemplateId() != null || requiresGuestForPlaceholders(raw)) && guest == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Для ссылки на документ или анкету выберите гостя с бронью");
        }
        if (request.documentTemplateId() != null && !raw.contains("{documentLink}")) {
            raw = raw.stripTrailing() + "\n\nДокумент для ознакомления и заполнения: {documentLink}";
        }

        String rendered = templateRenderService.render(raw, landlord, guest, property, request.documentTemplateId());

        ExternalMessagingPort port = resolvePort(config);
        boolean sent = port.sendReply(config, request.externalConversationId(), rendered);

        String displayName = request.guestDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = guest != null ? guestDisplayName(guest) : "Guest";
        }

        GuestMessage outbound = new GuestMessage();
        outbound.setProperty(property);
        outbound.setPlatform(request.platform());
        outbound.setExternalConversationId(request.externalConversationId());
        outbound.setExternalMessageId("out-" + System.currentTimeMillis());
        outbound.setGuestDisplayName(displayName);
        outbound.setContent(rendered);
        outbound.setDirection(MessageDirection.OUTBOUND);
        outbound.setDeliveryStatus(sent ? DeliveryStatus.SENT : DeliveryStatus.FAILED);
        outbound.setCreatedAt(OffsetDateTime.now());
        return messageRepository.save(outbound);
    }

    private static boolean requiresGuestForPlaceholders(String raw) {
        return raw.contains("{guestFormLink}")
                || raw.contains("{guestFormUrl}")
                || raw.contains("{guestFullName}")
                || raw.contains("{guestEmail}");
    }

    private boolean guestLinkedToProperty(Long ownerId, Long propertyId, Long guestId) {
        return bookingRepository.findByProperty_User_Id(ownerId).stream()
                .anyMatch(b -> propertyId.equals(b.getProperty().getId())
                        && b.getGuest() != null
                        && guestId.equals(b.getGuest().getId()));
    }

    private static String guestDisplayName(User g) {
        String n = g.getName() == null ? "" : g.getName();
        String s = g.getSurname() == null ? "" : g.getSurname();
        String joined = (n + " " + s).trim();
        return joined.isEmpty() ? ("Гость #" + g.getId()) : joined;
    }

    private int syncIntegration(ExternalIntegrationConfig config) {
        ExternalMessagingPort port = resolvePort(config);
        int imported = 0;
        for (InboundMessageDto inbound : port.fetchInboundMessages(config)) {
            if (messageRepository.existsByExternalMessageId(inbound.externalMessageId())) {
                continue;
            }
            Property property = propertyRepository.findById(inbound.propertyId())
                    .orElse(config.getProperty());

            GuestMessage message = new GuestMessage();
            message.setProperty(property);
            message.setPlatform(inbound.platform());
            message.setExternalConversationId(inbound.externalConversationId());
            message.setExternalMessageId(inbound.externalMessageId());
            message.setGuestDisplayName(inbound.guestDisplayName());
            message.setContent(inbound.content());
            message.setDirection(MessageDirection.INBOUND);
            message.setDeliveryStatus(DeliveryStatus.RECEIVED);
            message.setCreatedAt(inbound.createdAt() == null ? OffsetDateTime.now() : inbound.createdAt());
            messageRepository.save(message);
            imported++;
        }
        return imported;
    }

    private ExternalMessagingPort resolvePort(ExternalIntegrationConfig config) {
        return portRegistry.requirePort(config);
    }
}
