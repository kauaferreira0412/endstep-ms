package com.endstep.ms.repository;

import com.endstep.ms.entity.Game;
import com.endstep.ms.projection.StatEntryRow;
import com.endstep.ms.projection.StatsOverviewRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas agregadas para a home (numeros da plataforma, comandantes e cartas
 * mais jogados). Native SQL + projecoes de interface, padrao do projeto.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public interface StatsRepository extends JpaRepository<Game, Long> {

    @Query(value = """
            select
              (select count(*) from card_oracles)                                          as "cards",
              (select count(*) from card_printings)                                        as "printings",
              (select count(*) from games)                                                 as "games",
              (select count(*) from decks)                                                 as "decks",
              (select count(*) from users)                                                 as "players",
              (select count(*) from game_players)                                          as "seatsPlayed",
              (select to_char(max(finished_at), 'YYYY-MM-DD') from card_sync_runs
                where status = 'COMPLETED')                                                as "lastSync"
            """, nativeQuery = true)
    StatsOverviewRow overview();

    @Query(value = """
            select o.oracle_id::text                as "oracleId",
                   o.name                           as "name",
                   count(distinct gc.game_id)       as "games",
                   rp.image_normal                  as "image"
            from game_cards gc
            join card_oracles o on o.id = gc.oracle_card_id
            left join lateral (
                select cp.image_normal
                from card_printings cp
                where cp.oracle_card_id = o.id
                order by (cp.lang = 'en') desc, cp.released_at desc nulls last, cp.id desc
                limit 1
            ) rp on true
            where gc.zone = 'COMMAND'
            group by o.oracle_id, o.name, rp.image_normal
            order by count(distinct gc.game_id) desc, o.name asc
            limit :limit
            """, nativeQuery = true)
    List<StatEntryRow> topCommanders(@Param("limit") int limit);

    @Query(value = """
            select o.oracle_id::text                as "oracleId",
                   o.name                           as "name",
                   count(distinct gc.game_id)       as "games",
                   rp.image_small                   as "image"
            from game_cards gc
            join card_oracles o on o.id = gc.oracle_card_id
            left join lateral (
                select cp.image_small
                from card_printings cp
                where cp.oracle_card_id = o.id
                order by (cp.lang = 'en') desc, cp.released_at desc nulls last, cp.id desc
                limit 1
            ) rp on true
            where gc.oracle_card_id is not null and gc.zone <> 'COMMAND'
            group by o.oracle_id, o.name, rp.image_small
            order by count(distinct gc.game_id) desc, o.name asc
            limit :limit
            """, nativeQuery = true)
    List<StatEntryRow> topCards(@Param("limit") int limit);
}
