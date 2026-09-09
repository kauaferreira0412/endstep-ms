package com.endstep.ms.repository;

import com.endstep.ms.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de Room.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    List<Room> findByVisibilityAndStatusOrderByCreatedAtDesc(String visibility, String status);
}
