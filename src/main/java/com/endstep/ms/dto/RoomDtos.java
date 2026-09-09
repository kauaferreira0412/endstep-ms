package com.endstep.ms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Objetos de transferência de dados (RoomDtos).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class RoomDtos {
    private RoomDtos() {
    }

    public record CreateRoomRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank String format,
            @Min(2) @Max(6) int maxPlayers,
            boolean allowSpectators,
            boolean isPublic,
            @Size(max = 100) String password
    ) {
    }

    public record JoinRoomRequest(String password, boolean asSpectator) {
    }

    public record ChooseDeckRequest(Long deckId) {
    }

    public record RoomPlayerView(
            Long userId,
            String username,
            String role,
            Long deckId,
            String deckName,
            Integer seat,
            String status,
            boolean isHost
    ) {
    }

    public record RoomView(
            Long id,
            String code,
            String name,
            String format,
            int maxPlayers,
            boolean hasPassword,
            String visibility,
            boolean allowSpectators,
            String status,
            Long hostUserId,
            Long currentGameId,
            List<RoomPlayerView> players,
            Instant createdAt
    ) {
    }

    public record RoomSummary(
            String code,
            String name,
            String format,
            int players,
            int maxPlayers,
            boolean hasPassword,
            String status
    ) {
    }

    public record StartGameResponse(Long gameId) {
    }
}
