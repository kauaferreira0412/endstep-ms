package com.endstep.ms.service.game;

import java.util.Map;

/**
 * Mensagem de acao vinda do cliente via STOMP (/app/game/{id}/act).
 * type = uma das acoes suportadas pelo {@link GameEngine};
 * payload = campos livres lidos pelo engine conforme o type.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record GameActionMessage(String type, Map<String, Object> payload) {
    public Map<String, Object> payloadOrEmpty() {
        return payload == null ? Map.of() : payload;
    }

    public long getLong(String key, long def) {
        Object v = payloadOrEmpty().get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public Long getLongOrNull(String key) {
        Object v = payloadOrEmpty().get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public int getInt(String key, int def) {
        return (int) getLong(key, def);
    }

    public Double getDoubleOrNull(String key) {
        Object v = payloadOrEmpty().get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public String getString(String key) {
        Object v = payloadOrEmpty().get(key);
        return v == null ? null : String.valueOf(v);
    }

    public boolean getBool(String key, boolean def) {
        Object v = payloadOrEmpty().get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }
}
