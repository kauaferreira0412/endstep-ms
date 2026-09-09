package com.endstep.ms.controller;

import com.endstep.ms.dto.DeckDtos.DeckDetail;
import com.endstep.ms.dto.SocialDtos.SuggestDeckRequest;
import com.endstep.ms.dto.SocialDtos.SuggestionCardLine;
import com.endstep.ms.dto.SocialDtos.SuggestionView;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Sugestoes de deck recebidas e a acao de sugerir.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestions;

    public SuggestionController(SuggestionService suggestions) {
        this.suggestions = suggestions;
    }

    @GetMapping
    public List<SuggestionView> incoming(@AuthenticationPrincipal AuthPrincipal me) {
        return suggestions.listIncoming(me.id());
    }

    @GetMapping("/count")
    public Map<String, Long> count(@AuthenticationPrincipal AuthPrincipal me) {
        return Map.of("unread", suggestions.unreadCount(me.id()));
    }

    @GetMapping("/{id}/cards")
    public List<SuggestionCardLine> cards(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        return suggestions.cards(me.id(), id);
    }

    @PostMapping
    public void suggest(@AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody SuggestDeckRequest req) {
        suggestions.suggest(me.id(), req.deckId(), req.toUserId(), req.message());
    }

    @PostMapping("/{id}/import")
    public DeckDetail importAsDeck(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        return suggestions.importAsDeck(me.id(), id);
    }

    @DeleteMapping("/{id}")
    public void dismiss(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        suggestions.dismiss(me.id(), id);
    }
}
