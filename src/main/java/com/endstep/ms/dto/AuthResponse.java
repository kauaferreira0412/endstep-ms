package com.endstep.ms.dto;

/**
 * Objetos de transferência de dados (AuthResponse).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UserView user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserView user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
