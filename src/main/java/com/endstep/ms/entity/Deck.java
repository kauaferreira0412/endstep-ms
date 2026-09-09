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

/**
 * Entidade JPA Deck.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "decks",
        indexes = {
                @Index(name = "idx_decks_user_id", columnList = "user_id"),
                @Index(name = "idx_decks_folder_id", columnList = "folder_id")
        })
@Getter
@Setter
public class Deck {
    public enum Visibility { PRIVATE, UNLISTED, PUBLIC }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "suggested_by_user_id")
    private Long suggestedByUserId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 16)
    private String visibility = Visibility.PRIVATE.name();

    @Column(name = "color_identity", nullable = false, length = 16)
    private String colorIdentity = "";

    @Column(nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private int position;

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
