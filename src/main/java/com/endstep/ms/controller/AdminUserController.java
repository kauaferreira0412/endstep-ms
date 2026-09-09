package com.endstep.ms.controller;

import com.endstep.ms.dto.AdminDtos.AdminUserView;
import com.endstep.ms.dto.AdminDtos.PermissionCatalogItem;
import com.endstep.ms.dto.AdminDtos.SetPermissionsRequest;
import com.endstep.ms.service.AdminUserService;
import com.endstep.ms.service.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestao de usuarios e permissoes de tela. Somente ADMIN.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUsers;

    public AdminUserController(AdminUserService adminUsers) {
        this.adminUsers = adminUsers;
    }

    @GetMapping
    public List<AdminUserView> list() {
        return adminUsers.listUsers();
    }

    @GetMapping("/permissions")
    public List<PermissionCatalogItem> catalog() {
        return adminUsers.catalog();
    }

    @PutMapping("/{id}/permissions")
    public AdminUserView setPermissions(@PathVariable Long id,
                                        @RequestBody SetPermissionsRequest req,
                                        @AuthenticationPrincipal AuthPrincipal admin) {
        return adminUsers.setPermissions(id, req.permissions(), admin.id());
    }
}
