package com.rental.shortrental.user;

import com.rental.shortrental.audit.AuditService;
import com.rental.shortrental.booking.infrastructure.repository.BookingRepository;
import com.rental.shortrental.common.util.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestDataPublicControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private GuestDataPublicController controller;

    @Test
    void submitRejectsExpiredToken() {
        User guest = guest();
        guest.setDataCollectionTokenExpiresAt(Instant.now().minusSeconds(60));
        when(userRepository.findByDataCollectionToken("expired")).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> controller.submit("expired", validSubmission()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Link expired");
    }

    @Test
    void submitStoresPassportDataForValidGuestToken() {
        User guest = guest();
        guest.setDataCollectionTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(userRepository.findByDataCollectionToken("valid")).thenReturn(Optional.of(guest));

        controller.submit("valid", validSubmission());

        assertThat(guest.getPassportSeries()).isEqualTo("1234");
        assertThat(guest.getPassportNumber()).isEqualTo("567890");
        assertThat(guest.getPassportIssuedBy()).isEqualTo("ОВД");
        assertThat(guest.getPassportIssueDate()).isEqualTo(LocalDate.of(2020, 1, 20));
        assertThat(guest.getBirthDate()).isEqualTo(LocalDate.of(1995, 5, 10));
        assertThat(guest.getGuestPhone()).isEqualTo("+79990000000");
        verify(userRepository).save(guest);
    }

    private static GuestDataPublicController.GuestDataSubmission validSubmission() {
        return new GuestDataPublicController.GuestDataSubmission(
                "1234",
                "567890",
                "ОВД",
                LocalDate.of(2020, 1, 20),
                LocalDate.of(1995, 5, 10),
                "+79990000000"
        );
    }

    private static User guest() {
        User user = new User();
        user.setId(2L);
        user.setEmail("guest@example.test");
        user.setRole("GUEST");
        user.setDataCollectionToken("valid");
        return user;
    }
}
