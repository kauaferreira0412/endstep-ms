package com.endstep.ms.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Objetos de transferência de dados (CardDetail).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record CardDetail(
        UUID oracleId,
        String name,
        String manaCost,
        BigDecimal manaValue,
        String typeLine,
        String oracleText,
        String colors,
        String colorIdentity,
        String power,
        String toughness,
        String loyalty,
        String keywords,
        String layout,
        List<LegalityView> legalities,
        List<RulingView> rulings,
        List<PrintingView> printings,
        List<FaceView> faces
) {
}
