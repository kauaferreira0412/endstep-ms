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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Assento do jogador na partida: vida, marcadores, commander damage (secoes 22-23).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "game_players",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_game_players_game_user", columnNames = {"game_id", "user_id"}),
                @UniqueConstraint(name = "uk_game_players_game_seat", columnNames = {"game_id", "seat"})
        },
        indexes = {
                @Index(name = "idx_game_players_game_id", columnList = "game_id"),
                @Index(name = "idx_game_players_user_id", columnList = "user_id"),
                @Index(name = "idx_game_players_seat", columnList = "seat"),
                @Index(name = "idx_game_players_deck_id", columnList = "deck_id"),
                @Index(name = "idx_game_players_life_total", columnList = "life_total"),
                @Index(name = "idx_game_players_status", columnList = "status"),
                @Index(name = "idx_game_players_connected", columnList = "connected"),
                @Index(name = "idx_game_players_created_at", columnList = "created_at"),
                @Index(name = "idx_game_players_updated_at", columnList = "updated_at")
        })
@Getter
@Setter
public class GamePlayer {
    public enum Status { PLAYING, LOST, LEFT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int seat;

    @Column(name = "deck_id")
    private Long deckId;

    @Column(name = "life_total", nullable = false)
    private int lifeTotal = 20;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commander_damage", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> commanderDamage = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> counters = new HashMap<>();

    @Column(nullable = false, length = 16)
    private String status = Status.PLAYING.name();

    @Column(nullable = false)
    private boolean connected = true;

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
