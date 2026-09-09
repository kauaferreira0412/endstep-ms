package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Traducao em cache de uma carta para um idioma. Espelha V7__translations.sql
 * (Flyway = fonte; GIN trigram em name/type_line/oracle_text nao expressavel aqui).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "card_translations",
        uniqueConstraints = @UniqueConstraint(name = "uq_card_translations_oracle_lang",
                columnNames = {"oracle_card_id", "lang"}))
@Getter
@Setter
public class CardTranslation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "oracle_card_id", nullable = false)
    private Long oracleCardId;

    @Column(nullable = false, length = 8)
    private String lang = "pt";

    @Column(length = 512)
    private String name;

    @Column(name = "type_line", length = 512)
    private String typeLine;

    @Column(name = "oracle_text", columnDefinition = "text")
    private String oracleText;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
