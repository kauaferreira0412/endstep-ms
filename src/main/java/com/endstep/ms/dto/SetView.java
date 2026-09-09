package com.endstep.ms.dto;

import java.time.LocalDate;

/**
 * Objetos de transferência de dados (SetView).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record SetView(
        String code,
        String name,
        String setType,
        LocalDate releasedAt,
        Integer cardCount,
        String iconSvgUri
) {
}
