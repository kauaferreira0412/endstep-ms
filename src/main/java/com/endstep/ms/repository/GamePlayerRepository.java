package com.endstep.ms.repository;

import com.endstep.ms.entity.GamePlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de GamePlayer.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
    List<GamePlayer> findByGameIdOrderBySeat(Long gameId);

    Optional<GamePlayer> findByGameIdAndUserId(Long gameId, Long userId);
}
