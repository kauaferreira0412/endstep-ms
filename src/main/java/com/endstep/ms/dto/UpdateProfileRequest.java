package com.endstep.ms.dto;

import jakarta.validation.constraints.Size;

/**
 * Objetos de transferência de dados (UpdateProfileRequest).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 64) String displayName,
        @Size(max = 512) String avatarUrl
) {
}
