package com.rental.shortrental.web;

import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthWebController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthWebController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String name,
            @RequestParam String surname,
            Model model
    ) {
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Пользователь с таким email уже есть");
            return "register";
        }
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setSurname(surname);
        user.setRole(LandlordAccess.ROLE_LANDLORD);
        user.setOnboardingCompleted(false);
        userRepository.save(user);
        return "redirect:/login?registered";
    }
}
