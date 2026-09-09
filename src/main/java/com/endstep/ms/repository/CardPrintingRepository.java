package com.endstep.ms.repository;

import com.endstep.ms.entity.CardPrinting;
import com.endstep.ms.projection.PrintingProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de acesso a dados de CardPrinting.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CardPrintingRepository extends JpaRepository<CardPrinting, Long> {
    Optional<CardPrinting> findByScryfallId(UUID scryfallId);

    Optional<CardPrinting> findFirstByOracleCardIdOrderByReleasedAtDescIdDesc(Long oracleCardId);

    @Query(value = """
            select p.id                as "id",
                   p.scryfall_id       as "scryfallId",
                   p.set_code          as "setCode",
                   s.name              as "setName",
                   p.collector_number  as "collectorNumber",
                   p.rarity            as "rarity",
                   p.artist            as "artist",
                   p.lang              as "lang",
                   to_char(p.released_at, 'YYYY-MM-DD') as "releasedAt",
                   p.image_small       as "imageSmall",
                   p.image_normal      as "imageNormal",
                   p.image_large       as "imageLarge",
                   p.image_png         as "imagePng",
                   p.scryfall_uri      as "scryfallUri"
            from card_printings p
            left join sets s on s.id = p.set_id
            where p.oracle_card_id = :oracleDbId
            order by p.released_at desc nulls last, p.id desc
            """, nativeQuery = true)
    List<PrintingProjection> findPrintingViews(@Param("oracleDbId") long oracleDbId);
}
