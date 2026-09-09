package com.endstep.ms.repository;

import com.endstep.ms.entity.RoomPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de RoomPlayer.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface RoomPlayerRepository extends JpaRepository<RoomPlayer, Long> {
    List<RoomPlayer> findByRoomIdOrderByJoinedAt(Long roomId);

    Optional<RoomPlayer> findByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomIdAndRoleAndStatusNot(Long roomId, String role, String status);
}
