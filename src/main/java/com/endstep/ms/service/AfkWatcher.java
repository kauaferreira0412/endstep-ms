package com.endstep.ms.service;

import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.repository.GamePlayerRepository;
import com.endstep.ms.repository.GameRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Marca jogadores como AFK apos 5 minutos sem enviar nenhuma acao na partida.
 *
 * @author Kauã Ferreira
 * @since 2026-09-16
 */
@Service
public class AfkWatcher {

    private static final long AFK_AFTER_MS = 5 * 60 * 1000;

    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final GameEventPublisher publisher;

    public AfkWatcher(GameRepository games, GamePlayerRepository gamePlayers, GameEventPublisher publisher) {
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.publisher = publisher;
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkAfk() {
        List<Game> active = games.findByStatus(Game.Status.ACTIVE.name());
        Instant cutoff = Instant.now().minusMillis(AFK_AFTER_MS);
        for (Game game : active) {
            boolean changed = false;
            for (GamePlayer gp : gamePlayers.findByGameIdOrderBySeat(game.getId())) {
                if (gp.isAfk() || !GamePlayer.Status.PLAYING.name().equals(gp.getStatus())) {
                    continue;
                }
                Instant last = gp.getLastActiveAt() != null ? gp.getLastActiveAt() : game.getStartedAt();
                if (last == null || last.isBefore(cutoff)) {
                    gp.setAfk(true);
                    gamePlayers.save(gp);
                    changed = true;
                }
            }
            if (changed) {
                publisher.resyncAll(game.getId());
            }
        }
    }
}
