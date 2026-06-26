package com.rental.shortrental.property.infrastructure;

import com.rental.shortrental.property.infrastructure.entity.Property;
import com.rental.shortrental.property.infrastructure.repository.PropertyRepository;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/landlord/properties")
public class LandlordPropertyController {

    private final PropertyRepository propertyRepository;
    private final LandlordAccess landlordAccess;

    public LandlordPropertyController(PropertyRepository propertyRepository, LandlordAccess landlordAccess) {
        this.propertyRepository = propertyRepository;
        this.landlordAccess = landlordAccess;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Property create(Authentication authentication, @RequestBody CreatePropertyRequest request) {
        User landlord = landlordAccess.requireLandlord(authentication);
        Property property = new Property();
        property.setName(request.name());
        property.setAddress(request.address());
        property.setUser(landlord);
        return propertyRepository.save(property);
    }

    @GetMapping
    public List<Property> mine(Authentication authentication) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return propertyRepository.findByUser_Id(landlord.getId());
    }

    @GetMapping("/{id}")
    public Property getById(Authentication authentication, @PathVariable Long id) {
        User landlord = landlordAccess.requireLandlord(authentication);
        return landlordAccess.requireOwnedProperty(landlord, id);
    }

    public record CreatePropertyRequest(String name, String address) {
    }
}
