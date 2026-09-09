package com.endstep.ms.dto;

import java.util.List;

/**
 * Objetos de transferência de dados (ValidationResult).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record ValidationResult(
        String format,
        boolean legal,
        int deckSize,
        int commanderCount,
        String colorIdentity,
        List<Issue> issues
) {
    public enum Level { ERROR, WARNING }

    public record Issue(
            Level level,
            String code,
            String message,
            List<String> cards
    ) {
        public static Issue error(String code, String message) {
            return new Issue(Level.ERROR, code, message, List.of());
        }

        public static Issue error(String code, String message, List<String> cards) {
            return new Issue(Level.ERROR, code, message, cards);
        }

        public static Issue warn(String code, String message, List<String> cards) {
            return new Issue(Level.WARNING, code, message, cards);
        }
    }
}
