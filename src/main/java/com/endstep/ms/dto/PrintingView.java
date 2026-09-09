package com.endstep.ms.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Objetos de transferência de dados (PrintingView).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record PrintingView(
        long id,
        UUID scryfallId,
        String setCode,
        String setName,
        String collectorNumber,
        String rarity,
        String artist,
        String lang,
        LocalDate releasedAt,
        String imageSmall,
        String imageNormal,
        String imageLarge,
        String imagePng,
        String scryfallUri
) {
}
