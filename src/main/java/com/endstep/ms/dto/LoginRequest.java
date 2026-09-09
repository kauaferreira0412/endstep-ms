package com.endstep.ms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Objetos de transferência de dados (LoginRequest).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
