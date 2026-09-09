package com.endstep.ms.common;

/**
 * Tipo utilitário/base da aplicação: NotFoundException.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
