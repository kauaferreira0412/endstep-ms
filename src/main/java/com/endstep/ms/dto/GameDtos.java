package com.endstep.ms.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Objetos de transferência de dados (GameDtos).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class GameDtos {
    private GameDtos() {
    }

    public record TurnView(int turnNumber, int activeSeat, String phase, Long activeUserId) {
    }

    public record PlayerView(
            Long userId,
            String username,
            int seat,
            int life,
            boolean connected,
            String status,
            Map<String, Integer> commanderDamage,
            Map<String, Integer> counters,
            int libraryCount,
            int handCount,
            int graveyardCount,
            int exileCount,
            int commandCount,
            int battlefieldCount
    ) {
    }

    public record CardIdentity(
            Long oracleCardId,
            Long printingId,
            Long customArtId,
            String name,
            String displayName,
            String typeLine,
            String manaCost,
            Double manaValue,
            String oracleText,
            String colorIdentity,
            String imageSmall,
            String imageNormal,
            String imageLarge,
            boolean isToken,
            String tokenPt,
            String tokenColors,
            boolean hasBackFace,
            String backName,
            String backTypeLine,
            String backManaCost,
            String backOracleText,
            String backImageSmall,
            String backImageNormal,
            String backImageLarge
    ) {
    }

    public record GameCardView(
            Long id,
            Long ownerUserId,
            Long controllerUserId,
            String zone,
            int position,
            Double x,
            Double y,
            boolean tapped,
            boolean faceDown,
            int rotation,
            boolean transformed,
            Map<String, Integer> counters,
            CardIdentity identity
    ) {
    }

    public record LogLine(long sequence, String line, Instant at) {
    }

    public record GameSnapshot(
            Long gameId,
            String roomCode,
            String format,
            String status,
            Long meUserId,
            long lastSequence,
            TurnView turn,
            List<PlayerView> players,
            List<GameCardView> cards,
            List<LogLine> log
    ) {
    }

    public record GamePatch(
            long sequence,
            List<GameCardView> cards,
            java.util.List<Long> removed,
            List<PlayerView> players,
            TurnView turn,
            LogLine log
    ) {
    }

    public record ChatView(Long id, Long userId, String username, String body, String kind, Instant at) {
    }
}
