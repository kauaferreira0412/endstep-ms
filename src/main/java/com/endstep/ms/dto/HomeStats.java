package com.endstep.ms.dto;

import java.time.Instant;
import java.util.List;

/**
 * Payload da home: numeros gerais, rankings automaticos (comandantes e cartas
 * mais jogados em partidas de toda a plataforma) e lancamentos recentes.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public record HomeStats(
        Overview overview,
        List<Entry> topCommanders,
        List<Entry> topCards,
        List<RecentSet> recentSets,
        Instant generatedAt
) {

    public record Overview(
            long cards,
            long printings,
            long games,
            long decks,
            long players,
            long seatsPlayed,
            String lastSync
    ) {
    }

    public record Entry(String oracleId, String name, long games, String image) {
    }

    public record RecentSet(String code, String name, String releasedAt, String iconSvgUri) {
    }
}
