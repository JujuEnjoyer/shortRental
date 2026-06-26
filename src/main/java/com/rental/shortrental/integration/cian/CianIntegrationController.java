package com.rental.shortrental.integration.cian;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/integrations/cian")
public class CianIntegrationController {

    private final CianMessengerClient messengerClient;

    public CianIntegrationController(CianMessengerClient messengerClient) {
        this.messengerClient = messengerClient;
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        String accessKey = request.get("accessKey");
        if (accessKey == null || accessKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "accessKey required"));
        }
        try {
            messengerClient.verifyAccessKey(accessKey.trim());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Invalid Cian ACCESS KEY: " + e.getMessage()
            ));
        }
    }
}
