package com.endstep.ms.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deck que um usuario sugeriu para outro. Guarda um snapshot das cartas
 * (nao referencia o deck de origem). Espelha V10__friends_suggestions.sql.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Entity
@Table(name = "deck_suggestions", indexes = {
        @Index(name = "idx_deck_suggestions_to_user", columnList = "to_user_id, status"),
        @Index(name = "idx_deck_suggestions_from_user", columnList = "from_user_id")
})
@Getter
@Setter
public class DeckSuggestion {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CardSnapshot {
        public Long oracleCardId;
        public Long printingId;
        public String section;
        public int quantity;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "source_deck_id")
    private Long sourceDeckId;

    @Column(name = "deck_name", nullable = false, length = 160)
    private String deckName;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(length = 500)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<CardSnapshot> cards = new ArrayList<>();

    @Column(nullable = false, length = 16)
    private String status = "NEW";

    @Column(name = "imported_deck_id")
    private Long importedDeckId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
