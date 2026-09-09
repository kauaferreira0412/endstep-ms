package com.endstep.ms.repository;

import com.endstep.ms.entity.DeckSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeckSuggestionRepository extends JpaRepository<DeckSuggestion, Long> {

    List<DeckSuggestion> findByToUserIdOrderByCreatedAtDesc(Long toUserId);

    long countByToUserIdAndStatus(Long toUserId, String status);
}
