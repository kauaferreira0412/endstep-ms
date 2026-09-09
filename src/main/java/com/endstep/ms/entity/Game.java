package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Partida (estado autoritativo persistido; secao 31).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "games",
        indexes = {
                @Index(name = "idx_games_room_id", columnList = "room_id")
        })
@Getter
@Setter
public class Game {
    public enum Status { ACTIVE, FINISHED, ABANDONED }

    public enum Phase {
        UNTAP, UPKEEP, DRAW, MAIN1,
        COMBAT_BEGIN, ATTACKERS, BLOCKERS, COMBAT_DAMAGE, COMBAT_END,
        MAIN2, END, CLEANUP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(nullable = false, length = 16)
    private String status = Status.ACTIVE.name();

    @Column(name = "turn_number", nullable = false)
    private int turnNumber = 1;

    @Column(name = "active_seat", nullable = false)
    private int activeSeat = 0;

    @Column(nullable = false, length = 16)
    private String phase = Phase.UNTAP.name();

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence = 0;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
