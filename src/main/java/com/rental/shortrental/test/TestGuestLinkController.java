package com.rental.shortrental.test;

import com.rental.shortrental.messaging.application.TemplateRenderService;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test")
@org.springframework.context.annotation.Profile("dev")
public class TestGuestLinkController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemplateRenderService templateRenderService;

    public TestGuestLinkController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TemplateRenderService templateRenderService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.templateRenderService = templateRenderService;
    }

    @GetMapping("/create-guest-link")
    public Map<String, String> createTestGuestLink() {
        // Создаем тестового гостя
        String testEmail = "test-guest-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        if (userRepository.existsByEmail(testEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Test guest already exists");
        }

        User guest = new User();
        guest.setEmail(testEmail);
        guest.setPassword(passwordEncoder.encode("test123"));
        guest.setName("Тестовый");
        guest.setSurname("Гость");
        guest.setRole("GUEST");
        guest.setOnboardingCompleted(true);

        // Генерируем токен
        guest.setDataCollectionToken(UUID.randomUUID().toString());
        guest.setDataCollectionTokenExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));

        userRepository.save(guest);

        // Получаем ссылку
        String link = templateRenderService.guestFormUrl(guest);

        return Map.of(
            "guestId", guest.getId().toString(),
            "guestEmail", guest.getEmail(),
            "link", link,
            "token", guest.getDataCollectionToken(),
            "expiresAt", guest.getDataCollectionTokenExpiresAt().toString(),
            "message", "Тестовая ссылка создана. Перейдите по ссылке для проверки формы."
        );
    }

    @GetMapping("/guest-link-info")
    public Map<String, Object> getGuestLinkInfo() {
        // Находим любого гостя с токеном
        return userRepository.findFirstByRoleAndDataCollectionTokenIsNotNull("GUEST")
                .map(guest -> Map.<String, Object>of(
                    "guestId", guest.getId(),
                    "guestEmail", guest.getEmail(),
                    "link", templateRenderService.guestFormUrl(guest),
                    "token", guest.getDataCollectionToken(),
                    "expiresAt", guest.getDataCollectionTokenExpiresAt(),
                    "hasPassportData", guest.getPassportNumber() != null && !guest.getPassportNumber().isBlank()
                ))
                .orElse(Map.of("message", "Нет гостей с токенами. Создайте тестового гостя через /api/test/create-guest-link"));
    }

    @GetMapping("/quick-link")
    public Map<String, String> getQuickTestLink() {
        return userRepository.findFirstByRoleAndDataCollectionTokenIsNotNull("GUEST")
                .map(guest -> Map.of("link", templateRenderService.guestFormUrl(guest)))
                .orElseGet(() -> {
                    // Создаем тестового гостя если нет
                    User guest = new User();
                    guest.setEmail("quick-test-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com");
                    guest.setPassword(passwordEncoder.encode("test123"));
                    guest.setName("Тестовый");
                    guest.setSurname("Гость");
                    guest.setRole("GUEST");
                    guest.setOnboardingCompleted(true);
                    guest.setDataCollectionToken(UUID.randomUUID().toString());
                    guest.setDataCollectionTokenExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
                    userRepository.save(guest);
                    return Map.of("link", templateRenderService.guestFormUrl(guest));
                });
    }
}
