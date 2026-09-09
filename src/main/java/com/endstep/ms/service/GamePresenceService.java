package com.endstep.ms.service;

import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.repository.GamePlayerRepository;
import com.endstep.ms.service.game.EngineResult;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rastreia quais sessoes STOMP estao em qual partida para marcar
 * conectado/desconectado (endstep.txt secao 32). So marca offline quando a
 * ULTIMA sessao daquele usuario naquele jogo cai.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class GamePresenceService {
    private final GamePlayerRepository gamePlayers;
    private final GameEventPublisher publisher;

    private final ConcurrentHashMap<String, long[]> sessions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Set<String>> live = new ConcurrentHashMap<>();

    public GamePresenceService(GamePlayerRepository gamePlayers, GameEventPublisher publisher) {
        this.gamePlayers = gamePlayers;
        this.publisher = publisher;
    }

    @Transactional
    public void onJoin(String sessionId, long gameId, long userId) {
        if (sessionId != null) {
            sessions.put(sessionId, new long[]{gameId, userId});
            live.computeIfAbsent(key(gameId, userId), k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        }
        setConnected(gameId, userId, true);
    }

    @EventListener
    @Transactional
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = acc.getSessionId();
        long[] ref = sessionId == null ? null : sessions.remove(sessionId);
        if (ref == null) {
            return;
        }
        Set<String> set = live.get(key(ref[0], ref[1]));
        if (set != null) {
            set.remove(sessionId);
            if (!set.isEmpty()) {
                return;
            }
            live.remove(key(ref[0], ref[1]));
        }
        setConnected(ref[0], ref[1], false);
    }

    private static String key(long gameId, long userId) {
        return gameId + ":" + userId;
    }

    private void setConnected(long gameId, long userId, boolean connected) {
        GamePlayer gp = gamePlayers.findByGameIdAndUserId(gameId, userId).orElse(null);
        if (gp == null || gp.isConnected() == connected) {
            return;
        }
        gp.setConnected(connected);
        gamePlayers.save(gp);
        EngineResult r = new EngineResult()
                .eventType(connected ? "PLAYER_RECONNECTED" : "PLAYER_DISCONNECTED")
                .player(userId)
                .logLine(null);
        publisher.broadcastPatch(gameId, r);
    }
}
