package com.rental.shortrental.integration.avito;

import tools.jackson.databind.JsonNode;

public final class AvitoJson {

    private AvitoJson() {
    }

    public static String messageText(JsonNode msg) {
        JsonNode c = msg.get("content");
        if (c == null || c.isNull()) {
            return null;
        }
        if (c.hasNonNull("text")) {
            return c.get("text").asString();
        }
        return null;
    }

    public static String chatTitle(JsonNode chat) {
        if (chat.hasNonNull("title")) {
            return chat.get("title").asString();
        }
        JsonNode ctx = chat.get("context");
        if (ctx != null && !ctx.isNull()) {
            if (ctx.hasNonNull("title")) {
                return ctx.get("title").asString();
            }
            JsonNode value = ctx.get("value");
            if (value != null && value.hasNonNull("title")) {
                return value.get("title").asString();
            }
        }
        JsonNode users = chat.get("users");
        if (users != null && users.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode u : users) {
                if (u.hasNonNull("name")) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(u.get("name").asString());
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return null;
    }

    public static long chatUpdated(JsonNode chat) {
        if (chat.hasNonNull("updated")) {
            return chat.get("updated").asLong(0);
        }
        if (chat.hasNonNull("last_message_time")) {
            return chat.get("last_message_time").asLong(0);
        }
        JsonNode lm = chat.get("last_message");
        if (lm != null && lm.hasNonNull("created")) {
            return lm.get("created").asLong(0);
        }
        return 0;
    }

    public static String chatPreview(JsonNode chat) {
        JsonNode lm = chat.get("last_message");
        if (lm != null && !lm.isNull()) {
            String t = messageText(lm);
            if (t != null && !t.isBlank()) {
                return t;
            }
        }
        return null;
    }

    /**
     * Name of the other party in the chat (not the seller Avito account).
     */
    public static String counterpartyName(JsonNode chat, long sellerAvitoUserId) {
        JsonNode users = chat.get("users");
        if (users != null && users.isArray()) {
            for (JsonNode u : users) {
                long uid = u.path("id").asLong(0);
                if (uid != 0 && uid != sellerAvitoUserId) {
                    String fromUser = userDisplayName(u);
                    if (fromUser != null && !fromUser.isBlank()) {
                        return fromUser;
                    }
                }
            }
        }
        // Sometimes interlocutor is not in users[] — try common Avito shapes
        JsonNode ctx = chat.get("context");
        if (ctx != null) {
            JsonNode val = ctx.get("value");
            if (val != null) {
                String n = firstNonBlank(
                        textAt(val, "user_name"),
                        textAt(val, "buyer_name"),
                        textAt(val, "author_name")
                );
                if (n != null) {
                    return n;
                }
            }
        }
        JsonNode buyer = chat.get("buyer");
        if (buyer != null) {
            String n = userDisplayName(buyer);
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        JsonNode counterpart = chat.get("counterpart");
        if (counterpart != null) {
            String n = userDisplayName(counterpart);
            if (n != null && !n.isBlank()) {
                return n;
            }
        }
        JsonNode lm = chat.get("last_message");
        if (lm != null && "in".equalsIgnoreCase(lm.path("direction").asString())) {
            String n = messageAuthorLabel(lm);
            if (n != null && !n.startsWith("Пользователь ")) {
                return n;
            }
        }
        return null;
    }

    private static String userDisplayName(JsonNode u) {
        if (u == null || u.isNull()) {
            return null;
        }
        if (u.hasNonNull("name")) {
            return u.get("name").asString();
        }
        JsonNode profile = u.get("public_user_profile");
        if (profile != null && profile.hasNonNull("name")) {
            return profile.get("name").asString();
        }
        if (profile != null && profile.hasNonNull("title")) {
            return profile.get("title").asString();
        }
        return null;
    }

    private static String textAt(JsonNode n, String field) {
        return n != null && n.hasNonNull(field) ? n.get(field).asString() : null;
    }

    private static String firstNonBlank(String... xs) {
        if (xs == null) {
            return null;
        }
        for (String x : xs) {
            if (x != null && !x.isBlank()) {
                return x;
            }
        }
        return null;
    }

    public static String messageAuthorLabel(JsonNode msg) {
        JsonNode author = msg.get("author");
        if (author != null && author.hasNonNull("name")) {
            return author.get("name").asString();
        }
        if (msg.hasNonNull("author_name")) {
            return msg.get("author_name").asString();
        }
        JsonNode user = msg.get("user");
        if (user != null && user.hasNonNull("name")) {
            return user.get("name").asString();
        }
        long authorId = msg.path("author_id").asLong(0);
        if (authorId > 0) {
            return "Пользователь " + authorId;
        }
        return "Гость";
    }
}
