package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Conceito da carta (independente de impressao). Ex.: "Sol Ring".
 * Indices espelham V1__initial_card_schema.sql (Flyway = fonte de verdade; oracle_text,
 * keywords, name e type_line tambem tem indice GIN gin_trgm_ops la, nao expressavel em @Index).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "card_oracles")
@Getter
@Setter
public class CardOracle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oracle_id", nullable = false, unique = true)
    private UUID oracleId;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(name = "mana_cost", length = 256)
    private String manaCost;

    @Column(name = "mana_value")
    private BigDecimal manaValue;

    @Column(name = "type_line", length = 512)
    private String typeLine;

    @Column(name = "oracle_text", columnDefinition = "text")
    private String oracleText;

    @Column(length = 16)
    private String colors;

    @Column(name = "color_identity", length = 16)
    private String colorIdentity;

    @Column(length = 16)
    private String power;

    @Column(length = 16)
    private String toughness;

    @Column(length = 16)
    private String loyalty;

    @Column(columnDefinition = "text")
    private String keywords;

    @Column(length = 64)
    private String layout;

    @Column(nullable = false)
    private boolean reserved;

    @Column(name = "edhrec_rank")
    private Integer edhrecRank;

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
