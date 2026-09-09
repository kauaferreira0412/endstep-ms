package com.endstep.ms.repository;

import com.endstep.ms.entity.CardFace;
import com.endstep.ms.projection.FaceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositório de acesso a dados de CardFace.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CardFaceRepository extends JpaRepository<CardFace, Long> {
    @Modifying(flushAutomatically = true)
    @Query("delete from CardFace f where f.printingId = :printingId")
    void deleteByPrintingId(@Param("printingId") long printingId);

    @Query(value = """
            select f.face_index   as "faceIndex",
                   f.name         as "name",
                   f.mana_cost    as "manaCost",
                   f.type_line    as "typeLine",
                   f.oracle_text  as "oracleText",
                   f.image_normal as "imageNormal",
                   f.image_large  as "imageLarge"
            from card_faces f
            where f.printing_id = (
                select cp.id from card_printings cp
                where cp.oracle_card_id = :oracleDbId
                order by cp.released_at desc nulls last, cp.id desc
                limit 1
            )
            order by f.face_index
            """, nativeQuery = true)
    List<FaceProjection> findFaceViewsForNewestPrinting(@Param("oracleDbId") long oracleDbId);
}
