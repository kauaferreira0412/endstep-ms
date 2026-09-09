package com.endstep.ms.repository;

import com.endstep.ms.entity.DeckFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de DeckFolder.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface DeckFolderRepository extends JpaRepository<DeckFolder, Long> {
    List<DeckFolder> findByUserIdOrderByPositionAscNameAsc(Long userId);

    Optional<DeckFolder> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select f from DeckFolder f
            where f.userId = :userId
              and ((:parentId is null and f.parentId is null) or f.parentId = :parentId)
            order by f.position asc, f.name asc
            """)
    List<DeckFolder> findChildren(@Param("userId") Long userId, @Param("parentId") Long parentId);

    @Query("""
            select count(f) > 0 from DeckFolder f
            where f.userId = :userId
              and ((:parentId is null and f.parentId is null) or f.parentId = :parentId)
              and lower(f.name) = lower(:name)
              and (:excludeId is null or f.id <> :excludeId)
            """)
    boolean siblingNameTaken(@Param("userId") Long userId,
                             @Param("parentId") Long parentId,
                             @Param("name") String name,
                             @Param("excludeId") Long excludeId);
}
