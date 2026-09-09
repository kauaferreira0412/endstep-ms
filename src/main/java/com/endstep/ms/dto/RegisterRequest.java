package com.endstep.ms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Objetos de transferência de dados (RegisterRequest).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "apenas letras, numeros, ponto, hifen e underscore")
        String username,
        @NotBlank
        @Size(min = 2, max = 64)
        String displayName,
        @NotBlank
        @Email
        @Size(max = 255)
        String email,
        @NotBlank
        @Size(min = 8, max = 100)
        String password
) {
}
