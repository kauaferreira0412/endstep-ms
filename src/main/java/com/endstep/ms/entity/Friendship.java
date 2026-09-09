package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Amizade entre dois usuarios. Um par = uma linha, sempre com
 * {@code userLowId < userHighId}. Espelha V10__friends_suggestions.sql.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(name = "uq_friendships",
                columnNames = {"user_low_id", "user_high_id"}),
        indexes = {
                @Index(name = "idx_friendships_user_low", columnList = "user_low_id"),
                @Index(name = "idx_friendships_user_high", columnList = "user_high_id")
        })
@Getter
@Setter
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_low_id", nullable = false)
    private Long userLowId;

    @Column(name = "user_high_id", nullable = false)
    private Long userHighId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
