package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidade JPA Legality.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "legalities",
        uniqueConstraints = @UniqueConstraint(name = "uk_legalities_oracle_format",
                columnNames = {"oracle_card_id", "format"}))
@Getter
@Setter
public class Legality {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oracle_card_id", nullable = false)
    private Long oracleCardId;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(nullable = false, length = 32)
    private String status;
}
