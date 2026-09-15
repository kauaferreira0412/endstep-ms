package com.endstep.ms.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTOs de progressao: XP, nivel, titulo e conquistas.
 *
 * @author Kauã Ferreira
 * @since 2026-09-15
 */
public final class ProgressionDtos {

    private ProgressionDtos() {
    }

    public record UserProgressionView(
            long xp,
            int level,
            String title,
            int maxLevel,
            long xpIntoLevel,
            long xpForNextLevel,
            int gamesPlayed,
            int gamesWon,
            double winRate,
            List<AchievementView> achievements
    ) {
    }

    public record AchievementView(
            String code,
            String name,
            String description,
            String metric,
            int target,
            int progress,
            int xpReward,
            boolean unlocked,
            Instant unlockedAt
    ) {
    }

    public record LeaderboardEntry(
            Long userId,
            String username,
            String displayName,
            long xp,
            int level,
            String title,
            int gamesWon
    ) {
    }
}
