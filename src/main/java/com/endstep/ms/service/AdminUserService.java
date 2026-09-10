package com.endstep.ms.service;

import com.endstep.ms.common.NotFoundException;
import com.endstep.ms.dto.AdminDtos.AdminUserView;
import com.endstep.ms.dto.AdminDtos.PermissionCatalogItem;
import com.endstep.ms.entity.Role;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Listagem de usuarios e concessao de permissoes de tela (somente ADMIN).
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final PermissionService permissions;

    public AdminUserService(UserRepository users, PermissionService permissions) {
        this.users = users;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> listUsers() {
        List<User> all = users.findAll(Sort.by(Sort.Direction.ASC, "createdAt"));
        Map<Long, List<String>> permsByUser = permissions.codesOf(
                all.stream().map(User::getId).collect(Collectors.toSet()));
        return all.stream().map(u -> toView(u, permsByUser.getOrDefault(u.getId(), List.of()))).toList();
    }

    @Transactional
    public AdminUserView setPermissions(Long userId, List<String> wanted, Long adminId) {
        User u = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + userId));
        Set<String> set = wanted == null ? Set.of() : new HashSet<>(wanted);
        List<String> saved = permissions.setPermissions(userId, set, adminId);
        return toView(u, saved);
    }

    @Transactional
    public void deleteUser(Long userId, Long adminId) {
        if (userId.equals(adminId)) {
            throw new IllegalArgumentException("Voce nao pode excluir a sua propria conta.");
        }
        User u = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario nao encontrado: " + userId));
        boolean isAdmin = u.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
        if (isAdmin) {
            throw new IllegalArgumentException("Nao e possivel excluir outro ADMIN.");
        }
        users.delete(u);
    }

    public List<PermissionCatalogItem> catalog() {
        return AppPermission.all().stream()
                .map(p -> new PermissionCatalogItem(p.name(), p.label()))
                .toList();
    }

    private AdminUserView toView(User u, List<String> perms) {
        return new AdminUserView(
                u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(),
                u.getProvider(), u.getStatus(),
                u.getRoles().stream().map(Role::getName).sorted().toList(),
                perms, u.getCreatedAt());
    }
}
