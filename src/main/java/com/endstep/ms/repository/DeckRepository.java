package com.endstep.ms.repository;

import com.endstep.ms.entity.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de Deck.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface DeckRepository extends JpaRepository<Deck, Long> {
    List<Deck> findByUserIdOrderByPositionAscNameAsc(Long userId);

    Optional<Deck> findByIdAndUserId(Long id, Long userId);

    long countByFolderId(Long folderId);
}
