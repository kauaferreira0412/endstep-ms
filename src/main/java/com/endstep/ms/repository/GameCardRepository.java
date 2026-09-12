package com.endstep.ms.repository;

import com.endstep.ms.entity.GameCard;
import com.endstep.ms.projection.GameCardRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositório de acesso a dados de GameCard.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface GameCardRepository extends JpaRepository<GameCard, Long> {
    List<GameCard> findByGameId(Long gameId);

    List<GameCard> findByGameIdAndZoneAndOwnerUserId(Long gameId, String zone, Long ownerUserId);

    long countByGameIdAndZoneAndOwnerUserId(Long gameId, String zone, Long ownerUserId);

    @Query(value = """
            select gc.id                        as "id",
                   gc.owner_user_id             as "ownerUserId",
                   gc.controller_user_id        as "controllerUserId",
                   gc.zone                      as "zone",
                   gc.position                  as "position",
                   gc.x                         as "x",
                   gc.y                         as "y",
                   gc.tapped                    as "tapped",
                   gc.face_down                 as "faceDown",
                   gc.rotation                  as "rotation",
                   gc.transformed                as "transformed",
                   gc.counters::text            as "counters",
                   gc.revealed_to::text         as "revealedTo",
                   gc.oracle_card_id            as "oracleCardId",
                   gc.printing_id               as "printingId",
                   gc.custom_art_id             as "customArtId",
                   gc.is_token                  as "token",
                   gc.token_name                as "tokenName",
                   gc.token_pt                  as "tokenPt",
                   gc.token_colors              as "tokenColors",
                   gc.token_text                as "tokenText",
                   coalesce(o.name, gc.token_name)        as "name",
                   ca.display_name              as "displayName",
                   coalesce(o.type_line, 'Token')         as "typeLine",
                   o.mana_cost                  as "manaCost",
                   o.mana_value                 as "manaValue",
                   coalesce(o.oracle_text, gc.token_text) as "oracleText",
                   coalesce(o.color_identity, gc.token_colors) as "colorIdentity",
                   coalesce(ca.thumb_url, p.image_small,  rp.image_small)  as "imageSmall",
                   coalesce(ca.image_url, p.image_normal, rp.image_normal) as "imageNormal",
                   coalesce(ca.image_url, p.image_large,  rp.image_large)  as "imageLarge",
                   f1.name                      as "backName",
                   f1.type_line                 as "backTypeLine",
                   f1.mana_cost                 as "backManaCost",
                   f1.oracle_text               as "backOracleText",
                   f1.image_small               as "backImageSmall",
                   f1.image_normal              as "backImageNormal",
                   f1.image_large               as "backImageLarge"
            from game_cards gc
            left join card_oracles o on o.id = gc.oracle_card_id
            left join card_printings p on p.id = gc.printing_id
            left join custom_arts ca on ca.id = gc.custom_art_id
            left join lateral (
                select cp.id, cp.image_small, cp.image_normal, cp.image_large
                from card_printings cp
                where cp.oracle_card_id = o.id
                order by (cp.lang = 'en') desc, cp.released_at desc nulls last, cp.id desc
                limit 1
            ) rp on true
            left join card_faces f1 on f1.printing_id = coalesce(p.id, rp.id) and f1.face_index = 1
            where gc.game_id = :gameId
            order by gc.zone, gc.position, gc.id
            """, nativeQuery = true)
    List<GameCardRow> loadRows(@Param("gameId") Long gameId);
}
