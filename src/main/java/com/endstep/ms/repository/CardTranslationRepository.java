package com.endstep.ms.repository;

import com.endstep.ms.entity.CardTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de CardTranslation.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CardTranslationRepository extends JpaRepository<CardTranslation, Long> {
    Optional<CardTranslation> findByOracleCardIdAndLang(Long oracleCardId, String lang);

    List<CardTranslation> findByOracleCardIdInAndLang(Collection<Long> oracleCardIds, String lang);
}
