package com.endstep.ms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Objetos de transferência de dados (DeckDtos).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class DeckDtos {
    private DeckDtos() {
    }

    public record DeckSummary(
            Long id,
            Long folderId,
            String name,
            String format,
            String visibility,
            String colorIdentity,
            boolean favorite,
            int position,
            int cardCount,
            Instant updatedAt
    ) {
    }

    public record CreateDeckRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank String format,
            Long folderId
    ) {
    }

    public record UpdateDeckRequest(
            @Size(max = 160) String name,
            String format,
            Long folderId,
            String description,
            String visibility,
            Boolean favorite,
            Integer position
    ) {
    }

    public record DeckCardView(
            String oracleId,
            String name,
            String displayName,
            String manaCost,
            Double manaValue,
            String typeLine,
            String colorIdentity,
            String oracleText,
            String section,
            int quantity,
            Long printingId,
            Long customArtId,
            String imageSmall,
            String imageNormal,
            String imageLarge,
            String formatStatus,
            String commanderStatus
    ) {
    }

    public record CurvePoint(int manaValue, int count) {
    }

    public record DeckStats(
            int total,
            int mainboardCount,
            int lands,
            int nonlands,
            List<CurvePoint> curve,
            java.util.Map<String, Integer> colors,
            java.util.Map<String, Integer> types
    ) {
    }

    public record DeckDetail(
            Long id,
            Long folderId,
            List<String> folderPath,
            String name,
            String format,
            String description,
            String visibility,
            String colorIdentity,
            boolean favorite,
            FormatView formatRules,
            List<DeckCardView> cards,
            DeckStats stats,
            ValidationResult validation,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DeckCardChange(
            @NotBlank String oracleId,
            @NotBlank String section,
            int quantity,
            Long printingId
    ) {
    }

    public record ApplyCardsRequest(
            List<DeckCardChange> changes
    ) {
    }

    public record ImportDeckRequest(
            String name,
            @NotBlank String format,
            Long folderId,
            @NotBlank String text
    ) {
    }

    public record ImportResult(
            Long deckId,
            int importedLines,
            int importedCards,
            List<String> notFound,
            List<String> ambiguous
    ) {
    }
}
