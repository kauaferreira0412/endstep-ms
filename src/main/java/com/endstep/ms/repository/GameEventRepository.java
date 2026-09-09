package com.endstep.ms.repository;

import com.endstep.ms.entity.GameEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositório de acesso a dados de GameEvent.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface GameEventRepository extends JpaRepository<GameEvent, Long> {
    List<GameEvent> findByGameIdAndSequenceNumberGreaterThanOrderBySequenceNumber(Long gameId, long sequenceNumber);

    List<GameEvent> findByGameIdOrderBySequenceNumberDesc(Long gameId, Pageable pageable);
}
