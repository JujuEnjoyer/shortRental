package com.rental.shortrental.security;

import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LandlordLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public LandlordLoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if (!LandlordAccess.ROLE_LANDLORD.equalsIgnoreCase(user.getRole())) {
            response.sendRedirect(request.getContextPath() + "/?error=role");
            return;
        }
        String target = user.isOnboardingCompleted() ? "/app" : "/onboarding";
        response.sendRedirect(request.getContextPath() + target);
    }
}
