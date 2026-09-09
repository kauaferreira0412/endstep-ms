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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidade JPA CustomArt.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "custom_arts",
        indexes = {
                @Index(name = "idx_custom_arts_user_id", columnList = "user_id")
        })
@Getter
@Setter
public class CustomArt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "oracle_card_id", nullable = false)
    private Long oracleCardId;

    @Column(name = "base_printing_id")
    private Long basePrintingId;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "overlay_text", columnDefinition = "text")
    private String overlayText;

    @Column(name = "image_url", nullable = false, length = 512)
    private String imageUrl;

    @Column(name = "thumb_url", length = 512)
    private String thumbUrl;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "art_zoom", nullable = false)
    private BigDecimal artZoom = BigDecimal.ONE;

    @Column(name = "art_offset_x", nullable = false)
    private BigDecimal artOffsetX = BigDecimal.ZERO;

    @Column(name = "art_offset_y", nullable = false)
    private BigDecimal artOffsetY = BigDecimal.ZERO;

    @Column(name = "name_bar", nullable = false)
    private boolean nameBar;

    @Column(name = "text_bar", nullable = false)
    private boolean textBar;

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
