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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Participante de uma sala (jogador ou espectador) e o deck escolhido.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "room_players",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_players_room_user",
                columnNames = {"room_id", "user_id"}),
        indexes = {
                @Index(name = "idx_room_players_user_id", columnList = "user_id")
        })
@Getter
@Setter
public class RoomPlayer {
    public enum Role { PLAYER, SPECTATOR }

    public enum Status { JOINED, READY, LEFT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String role = Role.PLAYER.name();

    @Column(name = "deck_id")
    private Long deckId;

    @Column
    private Integer seat;

    @Column(nullable = false, length = 16)
    private String status = Status.JOINED.name();

    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (joinedAt == null) {
            joinedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
