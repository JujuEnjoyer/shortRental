package com.rental.shortrental.web;

import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebPageController {

    private final UserRepository userRepository;

    public WebPageController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/")
    public String landing() {
        return "landing";
    }

    @GetMapping("/onboarding")
    public String onboarding(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if (!LandlordAccess.ROLE_LANDLORD.equalsIgnoreCase(user.getRole())) {
            return "redirect:/";
        }
        if (user.isOnboardingCompleted()) {
            return "redirect:/app";
        }
        return "onboarding";
    }

    @GetMapping("/app")
    public String app(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if (!LandlordAccess.ROLE_LANDLORD.equalsIgnoreCase(user.getRole())) {
            return "redirect:/";
        }
        if (!user.isOnboardingCompleted()) {
            return "redirect:/onboarding";
        }
        return "app";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/app";
    }
}
