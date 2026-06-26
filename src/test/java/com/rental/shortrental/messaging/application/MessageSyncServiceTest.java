package com.rental.shortrental.messaging.application;

import com.rental.shortrental.booking.infrastructure.entity.Booking;
import com.rental.shortrental.booking.infrastructure.entity.ExternalPlatform;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.messaging.entity.DeliveryStatus;
import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import com.rental.shortrental.messaging.entity.GuestMessage;
import com.rental.shortrental.messaging.entity.IntegrationMode;
import com.rental.shortrental.messaging.entity.MessageDirection;
import com.rental.shortrental.messaging.repository.ExternalIntegrationConfigRepository;
import com.rental.shortrental.messaging.repository.GuestMessageRepository;
import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageSyncServiceTest {

    @Mock
    private ExternalIntegrationConfigRepository integrationRepository;
    @Mock
    private GuestMessageRepository messageRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private TemplateRenderService templateRenderService;
    @Mock
    private MessagingPortRegistry portRegistry;
    @Mock
    private ExternalMessagingPort port;

    private MessageSyncService service;

    @BeforeEach
    void setUp() {
        service = new MessageSyncService(
                integrationRepository,
                messageRepository,
                propertyRepository,
                userRepository,
                bookingRepository,
                templateRenderService,
                portRegistry
        );
    }

    @Test
    void syncOwnerMessagesImportsOnlyNewInboundMessages() {
        User landlord = user(1L, "owner@example.test", "LANDLORD");
        Property property = property(10L, landlord);
        ExternalIntegrationConfig config = config(property, ExternalPlatform.AVITO);
        InboundMessageDto inbound = new InboundMessageDto(
                property.getId(),
                ExternalPlatform.AVITO,
                "chat-1",
                "avito:chat-1:msg-1",
                "Гость",
                "Здравствуйте",
                OffsetDateTime.now()
        );
        when(integrationRepository.findByPropertyUserId(landlord.getId())).thenReturn(List.of(config));
        when(portRegistry.requirePort(config)).thenReturn(port);
        when(port.fetchInboundMessages(config)).thenReturn(List.of(inbound));
        when(messageRepository.existsByExternalMessageId(inbound.externalMessageId())).thenReturn(false);
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

        int imported = service.syncOwnerMessages(landlord.getId());

        assertThat(imported).isEqualTo(1);
        ArgumentCaptor<GuestMessage> captor = ArgumentCaptor.forClass(GuestMessage.class);
        verify(messageRepository).save(captor.capture());
        GuestMessage saved = captor.getValue();
        assertThat(saved.getProperty()).isSameAs(property);
        assertThat(saved.getPlatform()).isEqualTo(ExternalPlatform.AVITO);
        assertThat(saved.getDirection()).isEqualTo(MessageDirection.INBOUND);
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.RECEIVED);
        assertThat(saved.getContent()).isEqualTo("Здравствуйте");
    }

    @Test
    void replyRendersTemplateSendsThroughPortAndStoresOutboundMessage() {
        User landlord = user(1L, "owner@example.test", "LANDLORD");
        User guest = user(2L, "guest@example.test", "GUEST");
        Property property = property(10L, landlord);
        Booking booking = new Booking();
        booking.setProperty(property);
        booking.setGuest(guest);
        ExternalIntegrationConfig config = config(property, ExternalPlatform.AVITO);
        ReplyMessageRequest request = new ReplyMessageRequest(
                property.getId(),
                ExternalPlatform.AVITO,
                "chat-1",
                "Анна",
                "Добрый день, {guestFullName}",
                guest.getId(),
                null
        );
        when(integrationRepository.findByPropertyUserId(landlord.getId())).thenReturn(List.of(config));
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        when(userRepository.findById(landlord.getId())).thenReturn(Optional.of(landlord));
        when(userRepository.findById(guest.getId())).thenReturn(Optional.of(guest));
        when(bookingRepository.findByProperty_User_Id(landlord.getId())).thenReturn(List.of(booking));
        when(templateRenderService.render(request.content(), landlord, guest, property, null))
                .thenReturn("Добрый день, Анна Петрова");
        when(portRegistry.requirePort(config)).thenReturn(port);
        when(port.sendReply(config, request.externalConversationId(), "Добрый день, Анна Петрова"))
                .thenReturn(true);
        when(messageRepository.save(any(GuestMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuestMessage result = service.reply(landlord.getId(), request);

        assertThat(result.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(result.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(result.getContent()).isEqualTo("Добрый день, Анна Петрова");
        assertThat(result.getExternalConversationId()).isEqualTo("chat-1");
    }

    private static ExternalIntegrationConfig config(Property property, ExternalPlatform platform) {
        ExternalIntegrationConfig config = new ExternalIntegrationConfig();
        config.setProperty(property);
        config.setPlatform(platform);
        config.setMode(IntegrationMode.OPEN_API);
        config.setEnabled(true);
        return config;
    }

    private static Property property(Long id, User owner) {
        Property property = new Property();
        property.setId(id);
        property.setName("Demo flat");
        property.setUser(owner);
        return property;
    }

    private static User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setName(id == 2L ? "Анна" : "Иван");
        user.setSurname(id == 2L ? "Петрова" : "Иванов");
        return user;
    }
}
