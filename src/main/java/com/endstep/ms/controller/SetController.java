package com.endstep.ms.controller;
import com.endstep.ms.service.CardQueryService;

import com.endstep.ms.dto.SetView;
import com.endstep.ms.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de Set.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/sets")
public class SetController {
    private final CardQueryService cards;

    public SetController(CardQueryService cards) {
        this.cards = cards;
    }

    @GetMapping
    public PageResponse<SetView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        return cards.listSets(safePage, safeSize);
    }

    @GetMapping("/{code}")
    public SetView get(@PathVariable String code) {
        return cards.getSet(code);
    }
}
