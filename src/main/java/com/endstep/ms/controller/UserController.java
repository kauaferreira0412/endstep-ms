package com.endstep.ms.controller;

import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.dto.UpdateProfileRequest;
import com.endstep.ms.dto.UserView;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.UserRepository;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de User.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    private final PermissionService permissions;

    public UserController(UserRepository users, PermissionService permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthPrincipal principal) {
        User u = users.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado"));
        return UserView.of(u, permissions.codesOf(u.getId()));
    }

    @PutMapping("/me")
    @Transactional
    public UserView updateMe(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody UpdateProfileRequest req) {
        User u = users.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado"));
        if (req.displayName() != null && !req.displayName().isBlank()) {
            u.setDisplayName(req.displayName().trim());
        }
        if (req.avatarUrl() != null) {
            u.setAvatarUrl(req.avatarUrl().isBlank() ? null : req.avatarUrl().trim());
        }
        return UserView.of(users.save(u));
    }

    @GetMapping("/{id}")
    public UserView byId(@PathVariable Long id) {
        User u = users.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado"));
        return UserView.of(u);
    }
}
