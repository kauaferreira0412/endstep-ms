package com.endstep.ms.repository;

import com.endstep.ms.entity.CustomArt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de CustomArt.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CustomArtRepository extends JpaRepository<CustomArt, Long> {
    List<CustomArt> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CustomArt> findByUserIdAndOracleCardIdOrderByCreatedAtDesc(Long userId, Long oracleCardId);

    Optional<CustomArt> findByIdAndUserId(Long id, Long userId);
}
