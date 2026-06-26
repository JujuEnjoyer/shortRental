package com.rental.shortrental.user;

import com.rental.shortrental.security.LandlordAccess;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/landlord")
public class LandlordProfileController {

    private final UserRepository userRepository;
    private final LandlordAccess landlordAccess;

    public LandlordProfileController(UserRepository userRepository, LandlordAccess landlordAccess) {
        this.userRepository = userRepository;
        this.landlordAccess = landlordAccess;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = landlordAccess.requireLandlord(authentication);
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getSurname(),
                user.isOnboardingCompleted()
        );
    }

    @PatchMapping("/profile")
    public User updateProfile(Authentication authentication, @RequestBody UpdateProfileRequest request) {
        User user = landlordAccess.requireLandlord(authentication);
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.surname() != null && !request.surname().isBlank()) {
            user.setSurname(request.surname().trim());
        }
        return userRepository.save(user);
    }

    @PostMapping("/onboarding/complete")
    public MeResponse completeOnboarding(Authentication authentication) {
        User user = landlordAccess.requireLandlord(authentication);
        user.setOnboardingCompleted(true);
        userRepository.save(user);
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getSurname(),
                user.isOnboardingCompleted()
        );
    }

    public record MeResponse(Long id, String email, String name, String surname, boolean onboardingCompleted) {
    }

    public record UpdateProfileRequest(String name, String surname) {
    }
}
