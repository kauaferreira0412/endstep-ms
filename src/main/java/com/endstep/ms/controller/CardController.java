package com.endstep.ms.controller;
import com.endstep.ms.service.CardQueryService;

import com.endstep.ms.dto.CardDetail;
import com.endstep.ms.dto.CardSummary;
import com.endstep.ms.dto.PrintingView;
import com.endstep.ms.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints REST de Card.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/cards")
public class CardController {
    private final CardQueryService cards;

    public CardController(CardQueryService cards) {
        this.cards = cards;
    }

    @GetMapping("/search")
    public PageResponse<CardSummary> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return cards.search(q == null ? "" : q.trim(), safePage, safeSize);
    }

    @GetMapping("/{oracleId}")
    public CardDetail get(@PathVariable UUID oracleId) {
        return cards.detail(oracleId);
    }

    @GetMapping("/{oracleId}/printings")
    public List<PrintingView> printings(@PathVariable UUID oracleId) {
        return cards.printings(oracleId);
    }
}
