package com.endstep.ms.projection;

import java.util.UUID;

/**
 * Projeção de leitura PrintingProjection.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface PrintingProjection {
    long getId();

    UUID getScryfallId();

    String getSetCode();

    String getSetName();

    String getCollectorNumber();

    String getRarity();

    String getArtist();

    String getLang();

    String getReleasedAt();

    String getImageSmall();

    String getImageNormal();

    String getImageLarge();

    String getImagePng();

    String getScryfallUri();
}
