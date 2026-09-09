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
 * Sala / lobby (endstep.txt secoes 9-10).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "rooms",
        indexes = {
                @Index(name = "idx_rooms_host_user_id", columnList = "host_user_id"),
                @Index(name = "idx_rooms_visibility_status", columnList = "visibility, status")
        })
@Getter
@Setter
public class Room {
    public enum Visibility { PRIVATE, PUBLIC }

    public enum Status { OPEN, IN_GAME, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 12, unique = true)
    private String roomCode;

    @Column(name = "host_user_id", nullable = false)
    private Long hostUserId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(name = "max_players", nullable = false)
    private int maxPlayers = 4;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 16)
    private String visibility = Visibility.PRIVATE.name();

    @Column(name = "allow_spectators", nullable = false)
    private boolean allowSpectators = true;

    @Column(nullable = false, length = 16)
    private String status = Status.OPEN.name();

    @Column(name = "current_game_id")
    private Long currentGameId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

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
