package com.endstep.ms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Objetos de transferência de dados (RefreshRequest).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
