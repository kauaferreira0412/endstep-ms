package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Regras de um formato (data-driven; ver V3__decks.sql).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "formats")
@Getter
@Setter
public class FormatEntity {
    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "min_deck")
    private Integer minDeck;

    @Column(name = "max_deck")
    private Integer maxDeck;

    @Column(name = "max_copies", nullable = false)
    private int maxCopies = 4;

    @Column(nullable = false)
    private boolean singleton;

    @Column(name = "uses_command_zone", nullable = false)
    private boolean usesCommandZone;

    @Column(name = "default_life", nullable = false)
    private int defaultLife = 20;

    @Column(name = "enforce_color_identity", nullable = false)
    private boolean enforceColorIdentity;

    @Column(name = "sideboard_max", nullable = false)
    private int sideboardMax = 15;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
