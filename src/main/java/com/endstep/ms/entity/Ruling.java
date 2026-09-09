package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Indices espelham V1__initial_card_schema.sql (comment tambem tem GIN gin_trgm_ops la).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "rulings",
        indexes = {
                @Index(name = "idx_rulings_oracle_card_id", columnList = "oracle_card_id")
        })
@Getter
@Setter
public class Ruling {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oracle_card_id", nullable = false)
    private Long oracleCardId;

    @Column(length = 32)
    private String source;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;
}
