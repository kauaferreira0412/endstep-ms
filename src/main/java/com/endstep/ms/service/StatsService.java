package com.endstep.ms.service;

import com.endstep.ms.dto.HomeStats;
import com.endstep.ms.dto.HomeStats.Entry;
import com.endstep.ms.dto.HomeStats.Overview;
import com.endstep.ms.dto.HomeStats.RecentSet;
import com.endstep.ms.entity.SetEntity;
import com.endstep.ms.projection.StatEntryRow;
import com.endstep.ms.projection.StatsOverviewRow;
import com.endstep.ms.repository.SetRepository;
import com.endstep.ms.repository.StatsRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Monta o payload da home com cache curto em memoria (as agregacoes rodam no
 * banco; ninguem precisa do dado "ao vivo").
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Service
public class StatsService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int TOP_COMMANDERS = 8;
    private static final int TOP_CARDS = 12;
    private static final int RECENT_SETS = 6;

    private final StatsRepository stats;
    private final SetRepository sets;

    private volatile HomeStats cached;

    public StatsService(StatsRepository stats, SetRepository sets) {
        this.stats = stats;
        this.sets = sets;
    }

    @Transactional(readOnly = true)
    public HomeStats home() {
        HomeStats snap = cached;
        if (snap != null && Duration.between(snap.generatedAt(), Instant.now()).compareTo(TTL) < 0) {
            return snap;
        }
        HomeStats fresh = build();
        cached = fresh;
        return fresh;
    }

    private HomeStats build() {
        StatsOverviewRow o = stats.overview();
        Overview overview = new Overview(
                o.getCards(), o.getPrintings(), o.getGames(), o.getDecks(),
                o.getPlayers(), o.getSeatsPlayed(), o.getLastSync());

        List<Entry> commanders = stats.topCommanders(TOP_COMMANDERS).stream().map(StatsService::toEntry).toList();
        List<Entry> cards = stats.topCards(TOP_CARDS).stream().map(StatsService::toEntry).toList();

        List<RecentSet> recent = sets.findAllByOrderByReleasedAtDescCodeAsc(PageRequest.of(0, RECENT_SETS))
                .getContent().stream()
                .filter(s -> s.getReleasedAt() != null)
                .map(StatsService::toRecentSet)
                .toList();

        return new HomeStats(overview, commanders, cards, recent, Instant.now());
    }

    private static Entry toEntry(StatEntryRow r) {
        return new Entry(r.getOracleId(), r.getName(), r.getGames(), r.getImage());
    }

    private static RecentSet toRecentSet(SetEntity s) {
        return new RecentSet(s.getCode(), s.getName(),
                s.getReleasedAt() == null ? null : s.getReleasedAt().toString(), s.getIconSvgUri());
    }
}
