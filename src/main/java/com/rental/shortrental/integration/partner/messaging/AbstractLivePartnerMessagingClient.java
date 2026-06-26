package com.rental.shortrental.integration.partner.messaging;

import com.rental.shortrental.messaging.entity.ExternalIntegrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

abstract class AbstractLivePartnerMessagingClient implements LivePartnerMessagingClient {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final ObjectMapper objectMapper;
    protected final RestClient restClient;

    protected AbstractLivePartnerMessagingClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    protected JsonNode authorizedGetJson(String url, ExternalIntegrationConfig config) {
        try {
            String raw = restClient.get()
                    .uri(url)
                    .headers(h -> applyAuth(h, config))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (RestClientResponseException e) {
            log.warn("{} GET failed: {} {}", providerName(), e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    protected boolean authorizedPostJson(String url, ExternalIntegrationConfig config, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            restClient.post()
                    .uri(url)
                    .headers(h -> applyAuth(h, config))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (RestClientResponseException e) {
            log.warn("{} POST failed: {} {}", providerName(), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.warn("{} POST error", providerName(), e);
            return false;
        }
    }

    protected JsonNode authorizedPostJsonForResponse(String url, ExternalIntegrationConfig config, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            String raw = restClient.post()
                    .uri(url)
                    .headers(h -> applyAuth(h, config))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (RestClientResponseException e) {
            log.warn("{} POST failed: {} {}", providerName(), e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    protected void applyAuth(org.springframework.http.HttpHeaders headers, ExternalIntegrationConfig config) {
        String token = config.getAuthToken();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.trim());
        }
    }

    protected static boolean hasToken(ExternalIntegrationConfig config) {
        return config.getAuthToken() != null && !config.getAuthToken().isBlank();
    }

    protected static String endpoint(ExternalIntegrationConfig config, String fallback) {
        String configured = config.getOutboundEndpoint();
        if (configured == null || configured.isBlank()) {
            configured = config.getInboundEndpoint();
        }
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        String trimmed = configured.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") ? trimmed : fallback;
    }

    protected static String join(String base, String path) {
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    protected static String conversationId(JsonNode thread, String... fields) {
        String id = PartnerMessagingJson.firstText(thread, fields);
        if (!id.isBlank()) {
            return id;
        }
        long numeric = PartnerMessagingJson.firstLong(thread, fields);
        return numeric > 0 ? String.valueOf(numeric) : "";
    }
}
