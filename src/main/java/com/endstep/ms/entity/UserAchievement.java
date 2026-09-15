package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Progresso de um usuário numa conquista do catálogo.
 *
 * @author Kauã Ferreira
 * @since 2026-09-15
 */
@Entity
@Table(name = "user_achievements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_achievements_user_achievement",
                        columnNames = {"user_id", "achievement_id"})
        },
        indexes = {
                @Index(name = "idx_user_achievements_user_id", columnList = "user_id")
        })
@Getter
@Setter
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "achievement_id", nullable = false)
    private Long achievementId;

    @Column(nullable = false)
    private int progress = 0;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;
}
