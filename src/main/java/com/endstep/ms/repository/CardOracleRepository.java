package com.endstep.ms.repository;

import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.projection.CardSummaryProjection;
import com.endstep.ms.projection.OracleIdMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de acesso a dados de CardOracle.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CardOracleRepository extends JpaRepository<CardOracle, Long> {
    Optional<CardOracle> findByOracleId(UUID oracleId);

    @Query(value = """
            select o.oracle_id                          as "oracleId",
                   o.name                               as "name",
                   o.mana_cost                          as "manaCost",
                   o.mana_value                         as "manaValue",
                   o.type_line                          as "typeLine",
                   o.color_identity                     as "colorIdentity",
                   coalesce(p.printing_count, 0)        as "printingCount",
                   p.printing_id                        as "representativePrintingId",
                   p.set_code                           as "setCode",
                   p.image_small                        as "imageSmall",
                   p.image_normal                       as "imageNormal"
            from card_oracles o
            left join lateral (
                select cp.id                     as printing_id,
                       cp.set_code               as set_code,
                       cp.image_small            as image_small,
                       cp.image_normal           as image_normal,
                       (count(*) over ())::int   as printing_count
                from card_printings cp
                where cp.oracle_card_id = o.id
                order by (cp.lang = 'en') desc, cp.released_at desc nulls last, cp.id desc
                limit 1
            ) p on true
            where (:q = ''
                   or o.name ilike '%' || :q || '%'
                   or o.type_line ilike '%' || :q || '%')
            order by (o.name ilike '%' || :q || '%') desc,
                     (lower(o.name) = lower(:q)) desc,
                     (o.name ilike :q || '%') desc,
                     similarity(o.name, :q) desc,
                     o.name asc
            limit :size offset :offset
            """, nativeQuery = true)
    List<CardSummaryProjection> search(@Param("q") String q,
                                       @Param("size") int size,
                                       @Param("offset") long offset);

    @Query(value = """
            select count(*) from card_oracles o
            where (:q = ''
                   or o.name ilike '%' || :q || '%'
                   or o.type_line ilike '%' || :q || '%')
            """, nativeQuery = true)
    long countSearch(@Param("q") String q);

    @Query(value = "select oracle_id as \"oracleId\", id as \"id\" from card_oracles", nativeQuery = true)
    List<OracleIdMapping> findAllIdMappings();

    @Query("select o from CardOracle o where lower(o.name) = lower(:name)")
    List<CardOracle> findAllByNameIgnoreCase(@Param("name") String name);

    @Query("select o from CardOracle o where lower(o.name) like lower(concat(:frontName, ' // %'))")
    List<CardOracle> findByFrontFaceName(@Param("frontName") String frontName);
}
