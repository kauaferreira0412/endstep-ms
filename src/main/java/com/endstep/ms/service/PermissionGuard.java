package com.endstep.ms.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Exposto como {@code @perm} para uso em @PreAuthorize:
 * {@code @PreAuthorize("hasRole('ADMIN') or @perm.has('SYNC')")}. Consulta o
 * banco (nao o JWT), entao conceder/revogar vale sem novo login.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Component("perm")
public class PermissionGuard {

    private final PermissionService permissions;

    public PermissionGuard(PermissionService permissions) {
        this.permissions = permissions;
    }

    public boolean has(String code) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        return permissions.has(principal.id(), code);
    }
}
