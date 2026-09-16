package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Progressão de um usuário: XP, nível e histórico agregado de partidas.
 *
 * @author Kauã Ferreira
 * @since 2026-09-15
 */
@Entity
@Table(name = "user_stats")
@Getter
@Setter
public class UserStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private long xp = 0;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed = 0;

    @Column(name = "games_won", nullable = false)
    private int gamesWon = 0;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
