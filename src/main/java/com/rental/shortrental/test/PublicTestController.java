package com.rental.shortrental.test;

import com.rental.shortrental.document.application.DocumentLinkService;
import com.rental.shortrental.document.interfaces.DocumentTemplateRepository;
import com.rental.shortrental.messaging.application.TemplateRenderService;
import com.rental.shortrental.security.LandlordAccess;
import com.rental.shortrental.user.User;
import com.rental.shortrental.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/public/test")
public class PublicTestController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemplateRenderService templateRenderService;
    private final DocumentLinkService documentLinkService;
    private final DocumentTemplateRepository documentTemplateRepository;

    public PublicTestController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TemplateRenderService templateRenderService,
            DocumentLinkService documentLinkService,
            DocumentTemplateRepository documentTemplateRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.templateRenderService = templateRenderService;
        this.documentLinkService = documentLinkService;
        this.documentTemplateRepository = documentTemplateRepository;
    }

    @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
    public String testPage() {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Тестовые ссылки Short Rental</title>
                    <style>
                        body{margin:0;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;background:#f8fafc;color:#0f172a}
                        main{max-width:760px;margin:0 auto;padding:40px 20px}
                        h1{margin:0 0 10px;font-size:28px}
                        p{color:#475569;line-height:1.55}
                        .card{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:18px;margin-top:16px}
                        button,a.btn{display:inline-flex;align-items:center;justify-content:center;padding:10px 14px;border-radius:8px;border:0;background:#2563eb;color:#fff;font-weight:700;text-decoration:none;cursor:pointer}
                        button.secondary{background:#fff;color:#0f172a;border:1px solid #e2e8f0}
                        .row{display:flex;gap:10px;flex-wrap:wrap}
                        code{background:#eef2ff;padding:2px 6px;border-radius:6px}
                        pre{white-space:pre-wrap;background:#0f172a;color:#e2e8f0;border-radius:8px;padding:14px;overflow:auto}
                        .result{display:none;margin-top:14px}
                    </style>
                </head>
                <body>
                <main>
                    <h1>Тестовые ссылки гостя</h1>
                    <p>Откройте нужный сценарий одной кнопкой. Ссылки создаются локально, реальные площадки и реальные гости не затрагиваются.</p>
                    <div class="card">
                        <h2>Анкета гостя</h2>
                        <p>Гость заполняет паспортные данные и прикрепляет фото паспорта.</p>
                        <div class="row">
                            <button type="button" onclick="createLink('/public/test/create-test-guest-link')">Создать ссылку анкеты</button>
                        </div>
                    </div>
                    <div class="card">
                        <h2>Документ + анкета</h2>
                        <p>Гость сначала видит документ, затем отправляет паспортные данные на проверку арендодателю.</p>
                        <div class="row">
                            <button type="button" onclick="createLink('/public/test/create-test-document-link')">Создать ссылку документа</button>
                        </div>
                    </div>
                    <div id="result" class="result card">
                        <h2>Готово</h2>
                        <p><a id="openLink" class="btn" href="#" target="_blank" rel="noopener">Открыть как гость</a></p>
                        <pre id="json"></pre>
                    </div>
                </main>
                <script>
                    async function createLink(url) {
                        const r = await fetch(url);
                        const j = await r.json();
                        const link = j.formUrl || j.link;
                        document.getElementById('json').textContent = JSON.stringify(j, null, 2);
                        document.getElementById('openLink').href = link;
                        document.getElementById('result').style.display = 'block';
                    }
                </script>
                </body>
                </html>
                """;
    }

    private User findOrCreateTestLandlord() {
        return userRepository.findByEmail("test-landlord@test.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("test-landlord@test.com");
                    u.setPassword(passwordEncoder.encode("test123"));
                    u.setName("Тестовый");
                    u.setSurname("Арендодатель");
                    u.setRole(LandlordAccess.ROLE_LANDLORD);
                    u.setOnboardingCompleted(true);
                    return userRepository.save(u);
                });
    }

    private User findOrCreateTestGuest() {
        return userRepository.findByEmail("test-doc-guest@test.com")
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail("test-doc-guest@test.com");
                    u.setPassword(passwordEncoder.encode("test123"));
                    u.setName("Тестовый");
                    u.setSurname("Гость");
                    u.setRole("GUEST");
                    u.setOnboardingCompleted(true);
                    u.setDataCollectionToken(UUID.randomUUID().toString());
                    u.setDataCollectionTokenExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
                    return userRepository.save(u);
                });
    }

    @GetMapping("/create-test-guest-link")
    public Map<String, String> createTestGuestLink() {
        // Создаем тестового гостя
        String testEmail = "test-guest-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

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
            "message", "Тестовая ссылка создана. Перейдите по ссылке для проверки формы.",
            "formUrl", "http://localhost:8080/guest/data/" + guest.getDataCollectionToken(),
            "apiStatusUrl", "http://localhost:8080/api/public/guest-data/" + guest.getDataCollectionToken() + "/status"
        );
    }

    @GetMapping("/test-guest-link")
    public Map<String, Object> getTestGuestLink() {
        // Находим любого гостя с токеном или создаем нового
        return userRepository.findFirstByRoleAndDataCollectionTokenIsNotNull("GUEST")
                .map(guest -> {
                    String link = templateRenderService.guestFormUrl(guest);
                    return Map.<String, Object>of(
                        "guestId", guest.getId(),
                        "guestEmail", guest.getEmail(),
                        "link", link,
                        "formUrl", "http://localhost:8080/guest/data/" + guest.getDataCollectionToken(),
                        "token", guest.getDataCollectionToken(),
                        "expiresAt", guest.getDataCollectionTokenExpiresAt(),
                        "hasPassportData", guest.getPassportNumber() != null && !guest.getPassportNumber().isBlank(),
                        "apiStatusUrl", "http://localhost:8080/api/public/guest-data/" + guest.getDataCollectionToken() + "/status",
                        "apiSubmitUrl", "http://localhost:8080/api/public/guest-data/" + guest.getDataCollectionToken()
                    );
                })
                .orElseGet(() -> {
                    // Создаем нового гостя, если нет существующего
                    return toObjectMap(createTestGuestLink());
                });
    }

    private static Map<String, Object> toObjectMap(Map<String, String> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach(result::put);
        return result;
    }

    @GetMapping("/create-test-document-link")
    public Map<String, Object> createTestDocumentLink() {
        User landlord = findOrCreateTestLandlord();
        User guest = findOrCreateTestGuest();

        // Find first system template (CONTRACT)
        Long templateId = documentTemplateRepository.findBySystemTrueAndDocumentType("CONTRACT")
                .stream()
                .findFirst()
                .map(t -> t.getId())
                .orElse(null);

        String link = documentLinkService.createDocumentLink(landlord, guest, templateId);

        return Map.<String, Object>of(
            "landlordId", landlord.getId(),
            "landlordEmail", landlord.getEmail(),
            "guestId", guest.getId(),
            "guestEmail", guest.getEmail(),
            "formUrl", link,
            "message", "Ссылка для заполнения документа создана. Откройте в браузере."
        );
    }

    @GetMapping("/test-document-link")
    public Map<String, Object> getTestDocumentLink() {
        User landlord = findOrCreateTestLandlord();
        User guest = findOrCreateTestGuest();

        Long templateId = documentTemplateRepository.findBySystemTrueAndDocumentType("CONTRACT")
                .stream()
                .findFirst()
                .map(t -> t.getId())
                .orElse(null);

        String link = documentLinkService.createDocumentLink(landlord, guest, templateId);

        return Map.<String, Object>of(
            "landlordId", landlord.getId(),
            "landlordEmail", landlord.getEmail(),
            "guestId", guest.getId(),
            "guestEmail", guest.getEmail(),
            "formUrl", link,
            "message", "Ссылка для заполнения документа. Откройте в браузере."
        );
    }
}
