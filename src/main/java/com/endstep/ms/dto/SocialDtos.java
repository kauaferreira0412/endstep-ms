package com.endstep.ms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * DTOs de amigos e sugestoes de deck.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public final class SocialDtos {

    private SocialDtos() {
    }

    public record FriendView(
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            String status,
            Instant since
    ) {
    }

    public record AddFriendRequest(@NotBlank @Size(max = 64) String query) {
    }

    public record SuggestDeckRequest(
            long deckId,
            long toUserId,
            @Size(max = 500) String message
    ) {
    }

    public record SuggestionView(
            Long id,
            Long fromUserId,
            String fromUsername,
            String fromDisplayName,
            String deckName,
            String format,
            String message,
            int cardCount,
            String status,
            Long importedDeckId,
            Instant createdAt
    ) {
    }

    public record SuggestionCardLine(
            String name,
            String typeLine,
            String manaCost,
            String section,
            int quantity
    ) {
    }
}
