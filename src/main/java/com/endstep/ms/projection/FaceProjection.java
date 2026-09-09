package com.endstep.ms.projection;

/**
 * Projeção de leitura FaceProjection.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface FaceProjection {
    int getFaceIndex();

    String getName();

    String getManaCost();

    String getTypeLine();

    String getOracleText();

    String getImageNormal();

    String getImageLarge();
}
