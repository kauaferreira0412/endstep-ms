package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Face de carta dupla-face / split / adventure.
 * Indices espelham V1__initial_card_schema.sql (name e oracle_text tambem tem GIN gin_trgm_ops la).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "card_faces",
        uniqueConstraints = @UniqueConstraint(name = "uk_card_faces_printing_face",
                columnNames = {"printing_id", "face_index"}),
        indexes = {
                @Index(name = "idx_card_faces_printing_id", columnList = "printing_id")
        })
@Getter
@Setter
public class CardFace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printing_id", nullable = false)
    private Long printingId;

    @Column(name = "face_index", nullable = false)
    private int faceIndex;

    @Column(nullable = false, length = 512)
    private String name;

    @Column(name = "mana_cost", length = 256)
    private String manaCost;

    @Column(name = "type_line", length = 512)
    private String typeLine;

    @Column(name = "oracle_text", columnDefinition = "text")
    private String oracleText;

    @Column(length = 16)
    private String colors;

    @Column(length = 16)
    private String power;

    @Column(length = 16)
    private String toughness;

    @Column(length = 16)
    private String loyalty;

    @Column(name = "image_small", length = 512)
    private String imageSmall;

    @Column(name = "image_normal", length = 512)
    private String imageNormal;

    @Column(name = "image_large", length = 512)
    private String imageLarge;

    @Column(name = "image_png", length = 512)
    private String imagePng;
}
