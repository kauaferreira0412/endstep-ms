package com.endstep.ms.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item de resultado da busca no deck builder.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record CardSummary(
        UUID oracleId,
        String name,
        String manaCost,
        BigDecimal manaValue,
        String typeLine,
        String colorIdentity,
        int printingCount,
        Long representativePrintingId,
        String setCode,
        String imageSmall,
        String imageNormal
) {
}
