package com.endstep.ms.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTOs da area de administracao (gestao de usuarios e permissoes).
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserView(
            Long id,
            String username,
            String displayName,
            String email,
            String provider,
            String status,
            List<String> roles,
            List<String> permissions,
            Instant createdAt
    ) {
    }

    public record PermissionCatalogItem(String code, String label) {
    }

    public record SetPermissionsRequest(List<String> permissions) {
    }
}
