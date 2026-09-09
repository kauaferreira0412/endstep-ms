package com.endstep.ms.repository;

import com.endstep.ms.entity.Ruling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Repositório de acesso a dados de Ruling.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface RulingRepository extends JpaRepository<Ruling, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from Ruling")
    void deleteAllRulings();

    List<Ruling> findByOracleCardIdOrderByPublishedAtAsc(long oracleCardId);
}
