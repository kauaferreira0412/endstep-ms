package com.endstep.ms.entity;
import com.endstep.ms.common.SyncCounters;

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

import java.time.Instant;

/**
 * Controle de uma execucao do CardSyncJob (endstep.txt secao 5).
 * Indices espelham V1__initial_card_schema.sql (message tambem tem GIN gin_trgm_ops la).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Entity
@Table(name = "card_sync_runs",
        indexes = {
                @Index(name = "idx_card_sync_runs_status", columnList = "status")
        })
@Getter
@Setter
public class CardSyncRun {
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "bulk_type", length = 64)
    private String bulkType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "download_bytes")
    private Long downloadBytes;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "total_processed", nullable = false)
    private int totalProcessed;

    @Column(name = "inserted_count", nullable = false)
    private int insertedCount;

    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(columnDefinition = "text")
    private String message;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void applyCounters(SyncCounters c) {
        this.totalProcessed = c.processed;
        this.insertedCount = c.printingsInserted;
        this.updatedCount = c.printingsUpdated;
        this.errorCount = c.errors;
    }
}
