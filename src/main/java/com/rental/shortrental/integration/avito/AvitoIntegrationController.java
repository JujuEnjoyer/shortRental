package com.rental.shortrental.integration.avito;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api/integrations/avito")
public class AvitoIntegrationController {

    private final AvitoTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AvitoIntegrationController(
            AvitoTokenService tokenService,
            ObjectMapper objectMapper,
            @Qualifier("avitoRestClient") RestClient restClient
    ) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyCredentials(@RequestBody Map<String, String> request) {
        String clientId = request.get("clientId");
        String clientSecret = request.get("clientSecret");

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId and clientSecret required"));
        }

        try {
            String token = tokenService.getAccessToken(clientId, clientSecret);
            Long userId = fetchUserId(token);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "user_id", userId
            ));
        } catch (Exception e) {
            // Use 400 so session-based UI does not treat this as "logout" (401 triggers login redirect in landlord-api.js).
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid credentials: " + e.getMessage()
            ));
        }
    }

    private Long fetchUserId(String token) {
        String body = restClient.get()
                .uri("https://api.avito.ru/core/v1/accounts/self")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("id")) {
                return root.get("id").asLong();
            }
            throw new IllegalStateException("No id in response: " + body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse user id: " + e.getMessage(), e);
        }
    }
}
