package com.endstep.ms.dto;

/**
 * Objetos de transferência de dados (FaceView).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record FaceView(
        int faceIndex,
        String name,
        String manaCost,
        String typeLine,
        String oracleText,
        String imageNormal,
        String imageLarge
) {
}
