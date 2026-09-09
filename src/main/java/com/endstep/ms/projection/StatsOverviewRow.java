package com.endstep.ms.projection;

/**
 * Linha unica com os numeros gerais da plataforma para a home.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public interface StatsOverviewRow {
    long getCards();
    long getPrintings();
    long getGames();
    long getDecks();
    long getPlayers();
    long getSeatsPlayed();
    String getLastSync();
}
