package com.endstep.ms.repository;
import com.endstep.ms.entity.CardSyncRun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositório de acesso a dados de CardSyncRun.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CardSyncRunRepository extends JpaRepository<CardSyncRun, Long> {
    List<CardSyncRun> findTop10ByOrderByIdDesc();

    List<CardSyncRun> findByStatus(String status);

    boolean existsByStatus(String status);
}
