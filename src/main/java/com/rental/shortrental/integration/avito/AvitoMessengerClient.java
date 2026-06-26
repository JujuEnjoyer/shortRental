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
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Avito Messenger API client - uses per-user credentials.
 */
@Component
public class AvitoMessengerClient {

    private static final Logger log = LoggerFactory.getLogger(AvitoMessengerClient.class);
    private static final String API_BASE = "https://api.avito.ru";

    private final AvitoTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AvitoMessengerClient(
            AvitoTokenService tokenService,
            ObjectMapper objectMapper,
            @Qualifier("avitoRestClient") RestClient restClient
    ) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public List<JsonNode> fetchChats(String clientId, String clientSecret, long avitoUserId, int limit, int offset, String itemIdsCsv) {
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromUriString(API_BASE + "/messenger/v2/accounts/{userId}/chats")
                .encode(StandardCharsets.UTF_8)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .queryParam("unread_only", false);
        if (itemIdsCsv != null && !itemIdsCsv.isBlank()) {
            for (String id : itemIdsCsv.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    uri.queryParam("item_ids", trimmed);
                }
            }
        }
        String url = uri.buildAndExpand(avitoUserId).toUriString();
        try {
            String token = tokenService.getAccessToken(clientId, clientSecret);
            String raw = authorizedGet(url, token);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode chats = root.get("chats");
            if (chats == null || !chats.isArray()) {
                chats = root.path("result").path("chats");
            }
            List<JsonNode> list = new ArrayList<>();
            if (chats != null && chats.isArray()) {
                for (JsonNode c : chats) {
                    list.add(c);
                }
            }
            return list;
        } catch (RestClientResponseException e) {
            log.warn("Avito list chats failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.warn("Avito list chats error", e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public List<JsonNode> fetchMessages(String clientId, String clientSecret, long avitoUserId, String chatId, int limit, int offset) {
        String url = UriComponentsBuilder
                .fromUriString(API_BASE + "/messenger/v3/accounts/{userId}/chats/{chatId}/messages")
                .encode(StandardCharsets.UTF_8)
                .queryParam("limit", limit)
                .queryParam("offset", offset)
                .buildAndExpand(avitoUserId, chatId)
                .toUriString();
        try {
            String token = tokenService.getAccessToken(clientId, clientSecret);
            String raw = authorizedGet(url, token);
            JsonNode root = objectMapper.readTree(raw);
            return extractMessageNodes(root);
        } catch (RestClientResponseException e) {
            log.warn("Avito list messages failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.warn("Avito list messages error", e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public boolean sendTextMessage(String clientId, String clientSecret, long avitoUserId, String chatId, String text) {
        String url = API_BASE + "/messenger/v1/accounts/" + avitoUserId + "/chats/" + chatId + "/messages";
        Map<String, Object> body = new HashMap<>();
        body.put("type", "text");
        Map<String, String> message = new HashMap<>();
        message.put("text", text);
        body.put("message", message);
        try {
            String token = tokenService.getAccessToken(clientId, clientSecret);
            String json = objectMapper.writeValueAsString(body);
            authorizedPost(url, token, json);
            return true;
        } catch (RestClientResponseException e) {
            log.warn("Avito send message failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.warn("Avito send message error", e);
            return false;
        }
    }

    /**
     * Messenger /messages may return a JSON array or an object with {@code messages} / similar keys.
     */
    private List<JsonNode> extractMessageNodes(JsonNode root) {
        List<JsonNode> list = new ArrayList<>();
        if (root == null || root.isNull()) {
            return list;
        }
        if (root.isArray()) {
            root.forEach(list::add);
            return list;
        }
        String[] keys = {"messages", "items", "result", "data"};
        for (String key : keys) {
            JsonNode n = root.get(key);
            if (n != null && n.isArray()) {
                n.forEach(list::add);
                return list;
            }
        }
        JsonNode data = root.get("data");
        if (data != null && data.isObject()) {
            JsonNode nested = data.get("messages");
            if (nested != null && nested.isArray()) {
                nested.forEach(list::add);
                return list;
            }
        }
        Iterator<String> it = root.propertyNames().iterator();
        StringBuilder kb = new StringBuilder();
        while (it.hasNext()) {
            if (kb.length() > 0) {
                kb.append(",");
            }
            kb.append(it.next());
        }
        log.warn("Avito messages response is not a recognized array shape; keys: {}", kb);
        return list;
    }

    private String authorizedGet(String url, String token) {
        return restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    private void authorizedPost(String url, String token, String jsonBody) {
        restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);
    }
}
