package com.endstep.ms.projection;

import java.math.BigDecimal;

/**
 * Linha achatada de uma carta em jogo, com identidade e imagem resolvidas.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface GameCardRow {
    Long getId();
    Long getOwnerUserId();
    Long getControllerUserId();
    String getZone();
    Integer getPosition();
    BigDecimal getX();
    BigDecimal getY();
    Boolean getTapped();
    Boolean getFaceDown();
    Integer getRotation();
    String getCounters();
    String getRevealedTo();
    Long getOracleCardId();
    Long getPrintingId();
    Long getCustomArtId();
    Boolean getToken();
    String getTokenPt();
    String getTokenColors();
    String getTokenText();
    String getName();
    String getDisplayName();
    String getTypeLine();
    String getManaCost();
    java.math.BigDecimal getManaValue();
    String getOracleText();
    String getColorIdentity();
    String getImageSmall();
    String getImageNormal();
    String getImageLarge();
    Boolean getTransformed();
    String getBackName();
    String getBackTypeLine();
    String getBackManaCost();
    String getBackOracleText();
    String getBackImageSmall();
    String getBackImageNormal();
    String getBackImageLarge();
}
