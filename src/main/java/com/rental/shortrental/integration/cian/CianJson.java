package com.rental.shortrental.integration.cian;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public final class CianJson {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private CianJson() {
    }

    public static JsonNode result(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode result = root.get("result");
        return result == null || result.isNull() ? root : result;
    }

    public static String text(JsonNode message) {
        JsonNode content = message == null ? null : message.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        JsonNode text = content.get("text");
        return text == null || text.isNull() ? "" : text.asString("");
    }

    public static long epochSeconds(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return OffsetDateTime.parse(raw).toEpochSecond();
        } catch (DateTimeParseException ignored) {
            try {
                return java.time.LocalDateTime.parse(raw).atZone(MOSCOW).toEpochSecond();
            } catch (DateTimeParseException ignoredAgain) {
                return 0;
            }
        }
    }

    public static OffsetDateTime offsetDateTime(String raw) {
        long epoch = epochSeconds(raw);
        if (epoch <= 0) {
            return OffsetDateTime.now();
        }
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), MOSCOW);
    }

    public static String userLabel(JsonNode user) {
        if (user == null || user.isNull()) {
            return "";
        }
        String name = user.path("name").asString("");
        if (!name.isBlank()) {
            return name;
        }
        String first = user.path("firstName").asString("");
        String last = user.path("lastName").asString("");
        String joined = (first + " " + last).trim();
        if (!joined.isBlank()) {
            return joined;
        }
        long id = user.path("userId").asLong(0);
        return id > 0 ? "Пользователь " + id : "";
    }
}
