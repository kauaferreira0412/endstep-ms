package com.endstep.ms.service;

import com.endstep.ms.entity.UserPermission;
import com.endstep.ms.repository.UserPermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leitura e escrita das permissoes de tela por usuario.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Service
public class PermissionService {

    private final UserPermissionRepository repo;

    public PermissionService(UserPermissionRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<String> codesOf(Long userId) {
        return repo.findByUserId(userId).stream()
                .map(UserPermission::getPermission)
                .filter(AppPermission::isValid)
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<String>> codesOf(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return repo.findByUserIdIn(userIds).stream()
                .filter(p -> AppPermission.isValid(p.getPermission()))
                .collect(Collectors.groupingBy(
                        UserPermission::getUserId,
                        Collectors.mapping(UserPermission::getPermission,
                                Collectors.collectingAndThen(Collectors.toList(), l -> {
                                    l.sort(String::compareTo);
                                    return l;
                                }))));
    }

    public boolean has(Long userId, String code) {
        return repo.existsByUserIdAndPermission(userId, code);
    }

    @Transactional
    public List<String> setPermissions(Long userId, Set<String> wanted, Long grantedBy) {
        Set<String> valid = wanted.stream().filter(AppPermission::isValid).collect(Collectors.toSet());
        List<UserPermission> current = repo.findByUserId(userId);
        Set<String> currentCodes = current.stream().map(UserPermission::getPermission).collect(Collectors.toSet());

        List<UserPermission> toRemove = current.stream()
                .filter(p -> !valid.contains(p.getPermission()))
                .toList();
        repo.deleteAll(toRemove);

        List<UserPermission> toAdd = new ArrayList<>();
        for (String code : valid) {
            if (!currentCodes.contains(code)) {
                UserPermission p = new UserPermission();
                p.setUserId(userId);
                p.setPermission(code);
                p.setGrantedBy(grantedBy);
                toAdd.add(p);
            }
        }
        repo.saveAll(toAdd);

        return new HashSet<>(valid).stream().sorted().toList();
    }
}
