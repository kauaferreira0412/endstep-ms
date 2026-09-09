package com.endstep.ms.repository;

import com.endstep.ms.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositório de acesso a dados de Role.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}
