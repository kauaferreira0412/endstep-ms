package com.endstep.ms.service;

import com.endstep.ms.dto.GameDtos.ChatView;
import com.endstep.ms.dto.GameDtos.GameCardView;
import com.endstep.ms.dto.GameDtos.GamePatch;
import com.endstep.ms.dto.GameDtos.GameSnapshot;
import com.endstep.ms.dto.GameDtos.LogLine;
import com.endstep.ms.dto.GameDtos.PlayerView;
import com.endstep.ms.dto.GameDtos.TurnView;
import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.entity.User;
import com.endstep.ms.projection.GameCardRow;
import com.endstep.ms.repository.GamePlayerRepository;
import com.endstep.ms.repository.GameRepository;
import com.endstep.ms.repository.UserRepository;
import com.endstep.ms.service.game.EngineResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Expande um {@link EngineResult} em uma mensagem PATCH por destinatario
 * (jogadores + espectadores), aplicando a privacidade da mao. Tambem envia
 * o snapshot completo no JOIN/reconexao e as mensagens de chat.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class GameEventPublisher {
    private final SimpMessagingTemplate messaging;
    private final GameView gameView;
    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final UserRepository users;

    public GameEventPublisher(SimpMessagingTemplate messaging, GameView gameView, GameRepository games,
                              GamePlayerRepository gamePlayers, UserRepository users) {
        this.messaging = messaging;
        this.gameView = gameView;
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.users = users;
    }

    private String dest(long gameId) {
        return "/queue/game/" + gameId;
    }

    @Transactional(readOnly = true)
    public void broadcastPatch(long gameId, EngineResult result) {
        Game game = games.findById(gameId).orElse(null);
        if (game == null) {
            return;
        }
        List<GamePlayer> players = gamePlayers.findByGameIdOrderBySeat(gameId);
        Map<Long, GameCardRow> rowsById = gameView.rowsById(gameId);
        List<GameCardRow> allRows = new ArrayList<>(rowsById.values());
        Map<Long, String> names = users.findAllById(players.stream().map(GamePlayer::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        TurnView turn = result.turnChanged() ? gameView.turnView(game, players) : null;
        LogLine log = result.logLine() == null || result.logLine().isBlank()
                ? null : new LogLine(result.sequence(), result.logLine(), Instant.now());

        for (Long viewerId : gameView.participantUserIds(gameId)) {
            List<GameCardView> cards = new ArrayList<>();
            for (Long cardId : result.changedCardIds()) {
                GameCardRow row = rowsById.get(cardId);
                if (row != null) {
                    cards.add(gameView.cardView(row, viewerId));
                }
            }
            List<PlayerView> pv = new ArrayList<>();
            for (Long uid : result.changedPlayerUserIds()) {
                players.stream().filter(p -> p.getUserId().equals(uid)).findFirst().ifPresent(gp ->
                        pv.add(gameView.playerView(gp, names.getOrDefault(uid, "user#" + uid), allRows)));
            }
            GamePatch patch = new GamePatch(result.sequence(), cards,
                    new ArrayList<>(result.removedCardIds()), pv, turn, log);
            messaging.convertAndSendToUser(String.valueOf(viewerId), dest(gameId), envelope("PATCH", patch));
        }
    }

    @Transactional(readOnly = true)
    public void sendSnapshot(long gameId, long userId) {
        GameSnapshot snap = gameView.snapshot(gameId, userId);
        messaging.convertAndSendToUser(String.valueOf(userId), dest(gameId), envelope("GAME_STATE", snap));
    }

    @Transactional(readOnly = true)
    public void resyncAll(long gameId) {
        for (Long uid : gameView.participantUserIds(gameId)) {
            sendSnapshot(gameId, uid);
        }
    }

    public void broadcastChat(long gameId, List<Long> recipients, ChatView chat) {
        for (Long uid : recipients) {
            messaging.convertAndSendToUser(String.valueOf(uid), dest(gameId), envelope("CHAT", chat));
        }
    }

    public void broadcastPresence(long gameId, List<Long> recipients, Object presence) {
        for (Long uid : recipients) {
            messaging.convertAndSendToUser(String.valueOf(uid), dest(gameId), envelope("PRESENCE", presence));
        }
    }

    private Map<String, Object> envelope(String type, Object data) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("type", type);
        m.put("data", data);
        return m;
    }
}
