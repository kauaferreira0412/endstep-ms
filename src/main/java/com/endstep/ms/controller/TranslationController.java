package com.endstep.ms.controller;

import com.endstep.ms.dto.CardTranslationView;
import com.endstep.ms.service.TranslationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Traducao de cartas sob demanda (cache no banco). Aceita o UUID do oracle
 * (browse/deck builder) ou o id numerico de card_oracles (cartas em jogo).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
public class TranslationController {
    private final TranslationService translations;

    public TranslationController(TranslationService translations) {
        this.translations = translations;
    }

    @GetMapping("/api/translations/card")
    public CardTranslationView card(
            @RequestParam(required = false) UUID oracleId,
            @RequestParam(required = false) Long oracleCardId,
            @RequestParam(defaultValue = "pt") String lang
    ) {
        if (oracleId != null) {
            return translations.byOracleUuid(oracleId, lang);
        }
        if (oracleCardId != null) {
            return translations.byOracleCardId(oracleCardId, lang);
        }
        throw new ResponseStatusException(BAD_REQUEST, "Informe oracleId (UUID) ou oracleCardId (numerico)");
    }
}
