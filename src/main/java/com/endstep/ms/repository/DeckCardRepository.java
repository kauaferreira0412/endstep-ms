package com.endstep.ms.repository;

import com.endstep.ms.projection.DeckCardRow;
import com.endstep.ms.entity.DeckCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repositório de acesso a dados de DeckCard.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface DeckCardRepository extends JpaRepository<DeckCard, Long> {
    List<DeckCard> findByDeckId(Long deckId);

    List<DeckCard> findByDeckIdIn(java.util.Collection<Long> deckIds);

    Optional<DeckCard> findByDeckIdAndOracleCardIdAndSection(Long deckId, Long oracleCardId, String section);

    @Modifying(flushAutomatically = true)
    @Query("delete from DeckCard c where c.deckId = :deckId")
    void deleteByDeckId(@Param("deckId") Long deckId);

    @Query(value = """
            select dc.id                       as "id",
                   dc.section                  as "section",
                   dc.quantity                 as "quantity",
                   dc.printing_id              as "printingId",
                   dc.custom_art_id            as "customArtId",
                   ca.display_name             as "displayName",
                   o.oracle_id                 as "oracleId",
                   o.name                      as "name",
                   o.mana_cost                 as "manaCost",
                   o.mana_value                as "manaValue",
                   o.type_line                 as "typeLine",
                   o.color_identity            as "colorIdentity",
                   o.oracle_text               as "oracleText",
                   (select l.status from legalities l where l.oracle_card_id = o.id and l.format = :format)      as "formatStatus",
                   (select l.status from legalities l where l.oracle_card_id = o.id and l.format = 'commander')  as "commanderStatus",
                   coalesce(ca.thumb_url, p.image_small,  rp.image_small)  as "imageSmall",
                   coalesce(ca.image_url, p.image_normal, rp.image_normal) as "imageNormal",
                   coalesce(ca.image_url, p.image_large,  rp.image_large)  as "imageLarge"
            from deck_cards dc
            join card_oracles o on o.id = dc.oracle_card_id
            left join card_printings p on p.id = dc.printing_id
            left join custom_arts ca on ca.id = dc.custom_art_id
            left join lateral (
                select cp.image_small, cp.image_normal, cp.image_large
                from card_printings cp
                where cp.oracle_card_id = o.id
                order by (cp.lang = 'en') desc, cp.released_at desc nulls last, cp.id desc
                limit 1
            ) rp on true
            where dc.deck_id = :deckId
            order by dc.section, o.name
            """, nativeQuery = true)
    List<DeckCardRow> loadRows(@Param("deckId") Long deckId, @Param("format") String format);
}
