package com.rental.shortrental.security;

import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LandlordAccessTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PropertyRepository propertyRepository;

    @InjectMocks
    private LandlordAccess landlordAccess;

    @Test
    void requireLandlordReturnsAuthenticatedLandlord() {
        User landlord = user(1L, "owner@example.test", "LANDLORD");
        when(userRepository.findByEmail(landlord.getEmail())).thenReturn(Optional.of(landlord));

        User result = landlordAccess.requireLandlord(auth(landlord.getEmail()));

        assertThat(result).isSameAs(landlord);
    }

    @Test
    void requireLandlordRejectsGuestRole() {
        User guest = user(2L, "guest@example.test", "GUEST");
        when(userRepository.findByEmail(guest.getEmail())).thenReturn(Optional.of(guest));

        assertThatThrownBy(() -> landlordAccess.requireLandlord(auth(guest.getEmail())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Landlord role required");
    }

    @Test
    void requireOwnedPropertyRejectsForeignProperty() {
        User landlord = user(1L, "owner@example.test", "LANDLORD");
        User another = user(3L, "another@example.test", "LANDLORD");
        Property property = new Property();
        property.setId(10L);
        property.setUser(another);
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

        assertThatThrownBy(() -> landlordAccess.requireOwnedProperty(landlord, property.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Property does not belong to current user");
    }

    private static UsernamePasswordAuthenticationToken auth(String email) {
        return new UsernamePasswordAuthenticationToken(
                email,
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private static User user(Long id, String email, String role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
