package com.endstep.ms.dto;

import java.util.UUID;

/**
 * Traducao de uma carta para exibicao. {@code source}: "scryfall" (impressao
 * oficial), "google" (traducao de maquina do texto de regras) ou "none"
 * (sem traducao — os campos vem no idioma original).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record CardTranslationView(
        Long oracleCardId,
        UUID oracleId,
        String lang,
        String name,
        String typeLine,
        String oracleText,
        String source
) {
}
