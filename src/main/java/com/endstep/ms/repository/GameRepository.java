package com.endstep.ms.repository;

import com.endstep.ms.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de Game.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface GameRepository extends JpaRepository<Game, Long> {
    Optional<Game> findFirstByRoomIdOrderByIdDesc(Long roomId);

    List<Game> findByStatus(String status);
}
