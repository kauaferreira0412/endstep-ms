package com.endstep.ms.dto;

import java.time.LocalDate;

/**
 * Objetos de transferência de dados (RulingView).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record RulingView(
        LocalDate publishedAt,
        String comment
) {
}
