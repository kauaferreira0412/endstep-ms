package com.endstep.ms.repository;

import com.endstep.ms.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    Optional<Friendship> findByUserLowIdAndUserHighId(Long userLowId, Long userHighId);

    @Query("select f from Friendship f where f.userLowId = :userId or f.userHighId = :userId")
    List<Friendship> findAllForUser(@Param("userId") Long userId);
}
