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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado de uma carta na mesa (endstep.txt secao 55).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "game_cards",
        indexes = {
                @Index(name = "idx_game_cards_game_zone_owner", columnList = "game_id, zone, owner_user_id")
        })
@Getter
@Setter
public class GameCard {
    public enum Zone { LIBRARY, HAND, BATTLEFIELD, GRAVEYARD, EXILE, COMMAND, STACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "controller_user_id", nullable = false)
    private Long controllerUserId;

    @Column(name = "oracle_card_id")
    private Long oracleCardId;

    @Column(name = "printing_id")
    private Long printingId;

    @Column(name = "custom_art_id")
    private Long customArtId;

    @Column(name = "is_token", nullable = false)
    private boolean token = false;

    @Column(name = "token_name", length = 120)
    private String tokenName;

    @Column(name = "token_pt", length = 16)
    private String tokenPt;

    @Column(name = "token_colors", length = 8)
    private String tokenColors;

    @Column(name = "token_text", columnDefinition = "text")
    private String tokenText;

    @Column(nullable = false, length = 16)
    private String zone;

    @Column(nullable = false)
    private int position = 0;

    @Column
    private BigDecimal x;

    @Column
    private BigDecimal y;

    @Column(nullable = false)
    private boolean tapped = false;

    @Column(name = "face_down", nullable = false)
    private boolean faceDown = false;

    @Column(nullable = false)
    private int rotation = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> counters = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revealed_to", nullable = false, columnDefinition = "jsonb")
    private List<Long> revealedTo = new ArrayList<>();

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
