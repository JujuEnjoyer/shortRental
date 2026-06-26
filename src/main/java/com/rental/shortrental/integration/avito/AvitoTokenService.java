package com.rental.shortrental.integration.avito;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user OAuth token manager for Avito API (credentials come from each {@link com.rental.shortrental.messaging.entity.ExternalIntegrationConfig}).
 */
@Component
public class AvitoTokenService {

    private static final Logger log = LoggerFactory.getLogger(AvitoTokenService.class);

    private final AvitoProperties avitoProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    private static class TokenInfo {
        String token;
        Instant expiresAt;
    }

    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    public AvitoTokenService(
            AvitoProperties avitoProperties,
            ObjectMapper objectMapper,
            @Qualifier("avitoRestClient") RestClient restClient
    ) {
        this.avitoProperties = avitoProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public String getAccessToken(String clientId, String clientSecret) {
        String cacheKey = clientId;
        TokenInfo cached = tokenCache.get(cacheKey);
        Instant now = Instant.now();

        if (cached != null && now.isBefore(cached.expiresAt.minusSeconds(90))) {
            return cached.token;
        }

        return refreshToken(clientId, clientSecret);
    }

    private String refreshToken(String clientId, String clientSecret) {
        String scope = avitoProperties.getScope();
        if (scope == null || scope.isBlank()) {
            scope = "messenger:read messenger:write";
        }
        String form = "grant_type=" + enc("client_credentials")
                + "&client_id=" + enc(clientId.trim())
                + "&client_secret=" + enc(clientSecret.trim())
                + "&scope=" + enc(scope.trim().replaceAll("\\s+", " "));

        try {
            String body = restClient.post()
                    .uri(avitoProperties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            if (!root.hasNonNull("access_token")) {
                throw new IllegalStateException("No access_token in response: " + body);
            }

            String token = root.get("access_token").asString();
            long expiresIn = root.path("expires_in").asLong(3600);

            TokenInfo info = new TokenInfo();
            info.token = token;
            info.expiresAt = Instant.now().plusSeconds(expiresIn);
            tokenCache.put(clientId, info);

            return token;
        } catch (RestClientResponseException e) {
            log.warn("Avito OAuth failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Avito OAuth failed", e);
        } catch (Exception e) {
            log.warn("Avito OAuth error", e);
            throw new IllegalStateException("Avito OAuth error: " + e.getMessage(), e);
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
