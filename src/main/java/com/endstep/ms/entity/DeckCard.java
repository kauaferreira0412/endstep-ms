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
 * Entidade JPA DeckCard.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "deck_cards",
        uniqueConstraints = @UniqueConstraint(name = "uk_deck_cards_deck_oracle_section",
                columnNames = {"deck_id", "oracle_card_id", "section"}),
        indexes = {
                @Index(name = "idx_deck_cards_custom_art_id", columnList = "custom_art_id")
        })
@Getter
@Setter
public class DeckCard {
    public enum Section { COMMANDER, MAINBOARD, SIDEBOARD, MAYBEBOARD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @Column(name = "oracle_card_id", nullable = false)
    private Long oracleCardId;

    @Column(name = "printing_id")
    private Long printingId;

    @Column(name = "custom_art_id")
    private Long customArtId;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false, length = 16)
    private String section = Section.MAINBOARD.name();

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
