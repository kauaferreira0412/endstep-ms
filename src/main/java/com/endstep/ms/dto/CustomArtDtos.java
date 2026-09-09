package com.endstep.ms.dto;

import com.endstep.ms.entity.CustomArt;

import java.time.Instant;

/**
 * Objetos de transferência de dados (CustomArtDtos).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class CustomArtDtos {
    private CustomArtDtos() {
    }

    public record CustomArtView(
            Long id,
            String oracleId,
            String cardName,
            Long basePrintingId,
            String label,
            String displayName,
            String overlayText,
            String imageUrl,
            String thumbUrl,
            double artZoom,
            double artOffsetX,
            double artOffsetY,
            boolean nameBar,
            boolean textBar,
            Instant createdAt
    ) {
        public static CustomArtView of(CustomArt a, String oracleId, String cardName) {
            return new CustomArtView(a.getId(), oracleId, cardName, a.getBasePrintingId(), a.getLabel(),
                    a.getDisplayName(), a.getOverlayText(), a.getImageUrl(), a.getThumbUrl(),
                    a.getArtZoom().doubleValue(), a.getArtOffsetX().doubleValue(), a.getArtOffsetY().doubleValue(),
                    a.isNameBar(), a.isTextBar(), a.getCreatedAt());
        }
    }

    public record SetDeckCardArtRequest(
            String oracleId,
            String section,
            Long printingId,
            Long customArtId
    ) {
    }
}
