package com.endstep.ms.dto;

import com.endstep.ms.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * Objetos de transferência de dados (UserView).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record UserView(
        Long id,
        String username,
        String displayName,
        String email,
        String avatarUrl,
        String provider,
        String status,
        List<String> roles,
        List<String> permissions,
        Instant createdAt
) {
    public static UserView of(User u) {
        return of(u, List.of());
    }

    public static UserView of(User u, List<String> permissions) {
        return new UserView(
                u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getAvatarUrl(),
                u.getProvider(), u.getStatus(),
                u.getRoles().stream().map(r -> r.getName()).sorted().toList(),
                permissions == null ? List.of() : permissions,
                u.getCreatedAt());
    }
}
