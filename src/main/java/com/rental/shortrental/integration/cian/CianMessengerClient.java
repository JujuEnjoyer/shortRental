package com.rental.shortrental.integration.cian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CianMessengerClient {

    private static final Logger log = LoggerFactory.getLogger(CianMessengerClient.class);
    private static final String API_BASE = "https://public-api.cian.ru";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public CianMessengerClient(ObjectMapper objectMapper,
                               @Qualifier("cianRestClient") RestClient restClient) {
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public void verifyAccessKey(String accessKey) {
        authorizedGet(API_BASE + "/v1/get-my-balance", accessKey);
    }

    public List<JsonNode> fetchChats(String accessKey, Integer employeeId, int page, int pageSize) {
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromUriString(API_BASE + "/v1/get-chats")
                .encode(StandardCharsets.UTF_8)
                .queryParam("page", page)
                .queryParam("pageSize", pageSize)
                .queryParam("orderBy", "updatedAt")
                .queryParam("orderDir", "desc");
        if (employeeId != null) {
            uri.queryParam("employeeId", employeeId);
        }
        try {
            JsonNode root = objectMapper.readTree(authorizedGet(uri.toUriString(), accessKey));
            JsonNode result = CianJson.result(root);
            JsonNode chats = result == null ? null : result.get("chats");
            List<JsonNode> out = new ArrayList<>();
            if (chats != null && chats.isArray()) {
                chats.forEach(out::add);
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("Cian list chats failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cian list chats error: " + e.getMessage(), e);
        }
    }

    public List<JsonNode> fetchMessages(String accessKey, long chatId, int page, int pageSize, boolean readChat) {
        String url = UriComponentsBuilder
                .fromUriString(API_BASE + "/v1/get-messages")
                .encode(StandardCharsets.UTF_8)
                .queryParam("chatId", chatId)
                .queryParam("page", page)
                .queryParam("pageSize", pageSize)
                .queryParam("readChat", readChat)
                .toUriString();
        try {
            JsonNode root = objectMapper.readTree(authorizedGet(url, accessKey));
            JsonNode result = CianJson.result(root);
            JsonNode messages = result == null ? null : result.get("messages");
            List<JsonNode> out = new ArrayList<>();
            if (messages != null && messages.isArray()) {
                messages.forEach(out::add);
            }
            return out;
        } catch (RestClientResponseException e) {
            log.warn("Cian list messages failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cian list messages error: " + e.getMessage(), e);
        }
    }

    public boolean sendTextMessage(String accessKey, long chatId, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("chatId", chatId);
        body.put("content", Map.of("text", text));
        try {
            String json = objectMapper.writeValueAsString(body);
            authorizedPost(API_BASE + "/v1/send-message", accessKey, json);
            return true;
        } catch (RestClientResponseException e) {
            log.warn("Cian send message failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.warn("Cian send message error", e);
            return false;
        }
    }

    private String authorizedGet(String url, String accessKey) {
        return restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + accessKey)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    private void authorizedPost(String url, String accessKey, String jsonBody) {
        restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + accessKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);
    }
}
