package com.endstep.ms.controller;

import com.endstep.ms.dto.ProgressionDtos.LeaderboardEntry;
import com.endstep.ms.dto.ProgressionDtos.UserProgressionView;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.ProgressionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * XP, nivel, titulo, conquistas e ranking dos jogadores.
 *
 * @author Kauã Ferreira
 * @since 2026-09-15
 */
@RestController
@RequestMapping("/api/progression")
public class ProgressionController {

    private final ProgressionService progression;

    public ProgressionController(ProgressionService progression) {
        this.progression = progression;
    }

    @GetMapping("/me")
    public UserProgressionView me(@AuthenticationPrincipal AuthPrincipal principal) {
        return progression.view(principal.id());
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        return progression.leaderboard(Math.min(Math.max(limit, 1), 100));
    }
}
