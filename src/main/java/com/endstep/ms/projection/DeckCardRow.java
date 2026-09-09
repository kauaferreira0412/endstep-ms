package com.endstep.ms.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linha de deck_cards ja com os dados do oracle e legalidades (query nativa).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface DeckCardRow {
    Long getId();

    String getSection();

    int getQuantity();

    Long getPrintingId();

    Long getCustomArtId();

    String getDisplayName();

    UUID getOracleId();

    String getName();

    String getManaCost();

    BigDecimal getManaValue();

    String getTypeLine();

    String getColorIdentity();

    String getOracleText();

    String getFormatStatus();

    String getCommanderStatus();

    String getImageSmall();

    String getImageNormal();

    String getImageLarge();
}
