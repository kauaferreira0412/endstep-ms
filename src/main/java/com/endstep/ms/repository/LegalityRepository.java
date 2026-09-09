package com.endstep.ms.repository;

import com.endstep.ms.entity.Legality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositório de acesso a dados de Legality.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface LegalityRepository extends JpaRepository<Legality, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from Legality l where l.oracleCardId = :oracleCardId")
    void deleteByOracleCardId(@Param("oracleCardId") long oracleCardId);

    List<Legality> findByOracleCardIdOrderByFormatAsc(long oracleCardId);
}
