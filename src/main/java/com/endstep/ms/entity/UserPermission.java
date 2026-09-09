package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Permissao de tela concedida a um usuario (alem dos papeis). Espelha
 * V9__user_permissions.sql.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Entity
@Table(name = "user_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_permissions",
                columnNames = {"user_id", "permission"}),
        indexes = @Index(name = "idx_user_permissions_user_id", columnList = "user_id"))
@Getter
@Setter
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String permission;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @PrePersist
    void onCreate() {
        grantedAt = Instant.now();
    }
}
