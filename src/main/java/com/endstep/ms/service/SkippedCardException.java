package com.endstep.ms.service;

/**
 * Carta ignorada de proposito durante a sincronizacao (ex.: sem oracle_id).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public class SkippedCardException extends RuntimeException {
    public SkippedCardException(String message) {
        super(message);
    }
}
