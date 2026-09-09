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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Impressao especifica de uma carta (set / colecao / arte / idioma).
 * FKs mantidas como ids simples: leitura e feita com SQL nativo + joins.
 * Indices espelham V1__initial_card_schema.sql (flavor_text tambem tem GIN gin_trgm_ops la).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "card_printings",
        indexes = {
                @Index(name = "idx_card_printings_oracle_card_id", columnList = "oracle_card_id")
        })
@Getter
@Setter
public class CardPrinting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scryfall_id", nullable = false, unique = true)
    private UUID scryfallId;

    @Column(name = "oracle_card_id", nullable = false)
    private Long oracleCardId;

    @Column(name = "set_id")
    private Long setId;

    @Column(name = "set_code", length = 16)
    private String setCode;

    @Column(name = "collector_number", length = 32)
    private String collectorNumber;

    @Column(length = 32)
    private String rarity;

    @Column(length = 255)
    private String artist;

    @Column(name = "flavor_text", columnDefinition = "text")
    private String flavorText;

    @Column(nullable = false, length = 8)
    private String lang = "en";

    @Column(length = 64)
    private String layout;

    @Column(name = "border_color", length = 32)
    private String borderColor;

    @Column(length = 32)
    private String frame;

    @Column(nullable = false)
    private boolean promo;

    @Column(nullable = false)
    private boolean variation;

    @Column(name = "full_art", nullable = false)
    private boolean fullArt;

    @Column(name = "image_small", length = 512)
    private String imageSmall;

    @Column(name = "image_normal", length = 512)
    private String imageNormal;

    @Column(name = "image_large", length = 512)
    private String imageLarge;

    @Column(name = "image_png", length = 512)
    private String imagePng;

    @Column(name = "image_art_crop", length = 512)
    private String imageArtCrop;

    @Column(name = "image_border_crop", length = 512)
    private String imageBorderCrop;

    @Column(name = "released_at")
    private LocalDate releasedAt;

    @Column(name = "scryfall_uri", length = 512)
    private String scryfallUri;

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
