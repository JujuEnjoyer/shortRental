package com.rental.shortrental.integration.partner.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

final class PartnerMessagingJson {
    private PartnerMessagingJson() {
    }

    static List<JsonNode> arrayAt(JsonNode root, String... paths) {
        List<JsonNode> out = new ArrayList<>();
        if (root == null || root.isNull()) {
            return out;
        }
        if (root.isArray()) {
            root.forEach(out::add);
            return out;
        }
        for (String path : paths) {
            JsonNode node = nodeAt(root, path);
            if (node != null && node.isArray()) {
                node.forEach(out::add);
                return out;
            }
        }
        return out;
    }

    static JsonNode nodeAt(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (current == null || current.isNull()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    static String firstText(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = nodeAt(node, field);
            if (value != null && !value.isNull()) {
                String text = value.asString("");
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    static long firstLong(JsonNode node, String... fields) {
        if (node == null || node.isNull()) {
            return 0;
        }
        for (String field : fields) {
            JsonNode value = nodeAt(node, field);
            if (value != null && !value.isNull()) {
                long number = value.asLong(0);
                if (number > 0) {
                    return number;
                }
            }
        }
        return 0;
    }

    static OffsetDateTime dateTime(JsonNode node, String... fields) {
        long epoch = firstLong(node, fields);
        if (epoch > 0) {
            if (epoch > 10_000_000_000L) {
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
        }
        String raw = firstText(node, fields);
        if (!raw.isBlank()) {
            try {
                return OffsetDateTime.parse(raw);
            } catch (Exception ignored) {
                try {
                    return Instant.parse(raw).atOffset(ZoneOffset.UTC);
                } catch (Exception ignoredToo) {
                    return OffsetDateTime.now();
                }
            }
        }
        return OffsetDateTime.now();
    }

    static boolean incoming(JsonNode message, String ownerId) {
        String direction = firstText(message, "direction", "attributes.direction", "messageDirection", "type");
        if ("in".equalsIgnoreCase(direction)
                || "inbound".equalsIgnoreCase(direction)
                || "guest".equalsIgnoreCase(direction)
                || "from_guest".equalsIgnoreCase(direction)) {
            return true;
        }
        if ("out".equalsIgnoreCase(direction)
                || "outbound".equalsIgnoreCase(direction)
                || "host".equalsIgnoreCase(direction)
                || "from_host".equalsIgnoreCase(direction)) {
            return false;
        }
        String senderType = firstText(message, "senderType", "sender", "sender.type", "attributes.sender", "attributes.sender_type", "author.type");
        if ("guest".equalsIgnoreCase(senderType) || "customer".equalsIgnoreCase(senderType)) {
            return true;
        }
        if ("property".equalsIgnoreCase(senderType) || "host".equalsIgnoreCase(senderType) || "owner".equalsIgnoreCase(senderType)) {
            return false;
        }
        String senderId = firstText(message, "senderId", "sender.id", "author.id", "attributes.sender_id");
        return ownerId == null || ownerId.isBlank() || !ownerId.equals(senderId);
    }
}
