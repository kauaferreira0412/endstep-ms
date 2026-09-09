package com.endstep.ms.controller;

import com.endstep.ms.dto.GameDtos.GameSnapshot;
import com.endstep.ms.dto.GameDtos.LogLine;
import com.endstep.ms.entity.GameEvent;
import com.endstep.ms.repository.GameEventRepository;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.GameView;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * Endpoints REST de Game.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameView gameView;
    private final GameEventRepository gameEvents;

    public GameController(GameView gameView, GameEventRepository gameEvents) {
        this.gameView = gameView;
        this.gameEvents = gameEvents;
    }

    @GetMapping("/{id}")
    public GameSnapshot snapshot(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id) {
        requireParticipant(me, id);
        return gameView.snapshot(id, me.id());
    }

    @GetMapping("/{id}/history")
    public List<LogLine> history(@AuthenticationPrincipal AuthPrincipal me, @PathVariable long id,
                                 @RequestParam(defaultValue = "200") int limit) {
        requireParticipant(me, id);
        int capped = Math.max(1, Math.min(limit, 1000));
        List<GameEvent> rows = gameEvents.findByGameIdOrderBySequenceNumberDesc(id, PageRequest.of(0, capped));
        return rows.stream()
                .filter(e -> e.getLogLine() != null && !e.getLogLine().isBlank())
                .map(e -> new LogLine(e.getSequenceNumber(), e.getLogLine(), e.getCreatedAt()))
                .toList();
    }

    private void requireParticipant(AuthPrincipal me, long gameId) {
        if (!gameView.participantUserIds(gameId).contains(me.id())) {
            throw new ResponseStatusException(FORBIDDEN, "Voce nao participa desta partida");
        }
    }
}
