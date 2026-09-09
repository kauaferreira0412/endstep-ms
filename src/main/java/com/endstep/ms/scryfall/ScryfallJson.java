package com.endstep.ms.scryfall;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helpers de leitura defensiva do JSON do Scryfall.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class ScryfallJson {
    private ScryfallJson() {
    }

    public static UUID resolveOracleId(JsonNode card) {
        UUID top = uuidOrNull(card, "oracle_id");
        if (top != null) {
            return top;
        }
        JsonNode faces = card.get("card_faces");
        if (faces != null && faces.isArray()) {
            for (JsonNode f : faces) {
                UUID fid = uuidOrNull(f, "oracle_id");
                if (fid != null) {
                    return fid;
                }
            }
        }
        return null;
    }

    public static JsonNode firstFaceOrSelf(JsonNode card) {
        JsonNode faces = card.get("card_faces");
        if (faces != null && faces.isArray() && !faces.isEmpty()) {
            return faces.get(0);
        }
        return card;
    }

    public static JsonNode imageNode(JsonNode card) {
        JsonNode img = card.get("image_uris");
        if (img != null && img.isObject()) {
            return img;
        }
        JsonNode faces = card.get("card_faces");
        if (faces != null && faces.isArray() && !faces.isEmpty()) {
            return faces.get(0).get("image_uris");
        }
        return null;
    }

    public static String imageUri(JsonNode img, String key) {
        if (img == null || !img.hasNonNull(key)) {
            return null;
        }
        return img.get(key).asText();
    }

    public static String str(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String v = node.get(field).asText();
        return v.isEmpty() ? null : v;
    }

    public static String strOr(JsonNode node, String field, String fallback) {
        String v = str(node, field);
        return v != null ? v : fallback;
    }

    public static String joinArray(JsonNode array, String sep) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode n : array) {
            parts.add(n.asText());
        }
        return String.join(sep, parts);
    }

    public static Integer integerOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || !node.get(field).isNumber()) {
            return null;
        }
        return node.get(field).asInt();
    }

    public static BigDecimal bigDecimalOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || !node.get(field).isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(node.get(field).asDouble());
    }

    public static LocalDate dateOrNull(JsonNode node, String field) {
        String v = str(node, field);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v.length() >= 10 ? v.substring(0, 10) : v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static UUID uuidOrNull(JsonNode node, String field) {
        String v = str(node, field);
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
