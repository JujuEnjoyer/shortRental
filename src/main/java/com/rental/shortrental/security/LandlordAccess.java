package com.rental.shortrental.security;

import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LandlordAccess {

    public static final String ROLE_LANDLORD = "LANDLORD";

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    public LandlordAccess(UserRepository userRepository, PropertyRepository propertyRepository) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
    }

    public User requireLandlord(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!ROLE_LANDLORD.equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Landlord role required");
        }
        return user;
    }

    public Property requireOwnedProperty(User landlord, Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found"));
        if (property.getUser() == null || !property.getUser().getId().equals(landlord.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Property does not belong to current user");
        }
        return property;
    }
}
