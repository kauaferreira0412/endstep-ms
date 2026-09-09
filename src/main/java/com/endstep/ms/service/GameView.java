package com.endstep.ms.service;

import com.endstep.ms.dto.GameDtos.CardIdentity;
import com.endstep.ms.dto.GameDtos.GameCardView;
import com.endstep.ms.dto.GameDtos.GameSnapshot;
import com.endstep.ms.dto.GameDtos.LogLine;
import com.endstep.ms.dto.GameDtos.PlayerView;
import com.endstep.ms.dto.GameDtos.TurnView;
import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.GameCard;
import com.endstep.ms.entity.GameEvent;
import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.entity.Room;
import com.endstep.ms.entity.RoomPlayer;
import com.endstep.ms.entity.User;
import com.endstep.ms.projection.GameCardRow;
import com.endstep.ms.repository.GameCardRepository;
import com.endstep.ms.repository.GameEventRepository;
import com.endstep.ms.repository.GamePlayerRepository;
import com.endstep.ms.repository.GameRepository;
import com.endstep.ms.repository.RoomPlayerRepository;
import com.endstep.ms.repository.RoomRepository;
import com.endstep.ms.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Monta o estado da partida por destinatario, aplicando a privacidade da mao
 * (endstep.txt secao 56): identidade de cartas em zona oculta so vai para o dono
 * ou para quem esta em revealed_to.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class GameView {
    private static final int LOG_TAIL = 80;

    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final GameCardRepository gameCards;
    private final GameEventRepository gameEvents;
    private final RoomRepository rooms;
    private final RoomPlayerRepository roomPlayers;
    private final UserRepository users;
    private final ObjectMapper mapper;

    public GameView(GameRepository games, GamePlayerRepository gamePlayers, GameCardRepository gameCards,
                    GameEventRepository gameEvents, RoomRepository rooms, RoomPlayerRepository roomPlayers,
                    UserRepository users, ObjectMapper mapper) {
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.gameCards = gameCards;
        this.gameEvents = gameEvents;
        this.rooms = rooms;
        this.roomPlayers = roomPlayers;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public GameSnapshot snapshot(long gameId, long viewerId) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Partida nao encontrada"));
        Room room = rooms.findById(game.getRoomId()).orElse(null);
        List<GamePlayer> players = gamePlayers.findByGameIdOrderBySeat(gameId);
        List<GameCardRow> rows = gameCards.loadRows(gameId);

        Map<Long, User> userById = users.findAllById(players.stream().map(GamePlayer::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<PlayerView> playerViews = new ArrayList<>();
        for (GamePlayer gp : players) {
            User u = userById.get(gp.getUserId());
            playerViews.add(playerView(gp, u != null ? u.getUsername() : ("user#" + gp.getUserId()), rows));
        }

        List<GameCardView> cardViews = new ArrayList<>(rows.size());
        for (GameCardRow r : rows) {
            cardViews.add(cardView(r, viewerId));
        }

        List<LogLine> log = new ArrayList<>();
        List<GameEvent> tail = gameEvents.findByGameIdOrderBySequenceNumberDesc(gameId, PageRequest.of(0, LOG_TAIL));
        for (int i = tail.size() - 1; i >= 0; i--) {
            GameEvent e = tail.get(i);
            if (e.getLogLine() != null && !e.getLogLine().isBlank()) {
                log.add(new LogLine(e.getSequenceNumber(), e.getLogLine(), e.getCreatedAt()));
            }
        }

        return new GameSnapshot(
                gameId,
                room != null ? room.getRoomCode() : null,
                game.getFormat(),
                game.getStatus(),
                viewerId,
                game.getLastSequence(),
                turnView(game, players),
                playerViews,
                cardViews,
                log);
    }

    public TurnView turnView(Game game, List<GamePlayer> players) {
        Long activeUserId = players.stream()
                .filter(p -> p.getSeat() == game.getActiveSeat())
                .map(GamePlayer::getUserId)
                .findFirst().orElse(null);
        return new TurnView(game.getTurnNumber(), game.getActiveSeat(), game.getPhase(), activeUserId);
    }

    public PlayerView playerView(GamePlayer gp, String username, List<GameCardRow> allRows) {
        int lib = 0, hand = 0, gy = 0, exile = 0, cmd = 0, bf = 0;
        for (GameCardRow r : allRows) {
            if (!gp.getUserId().equals(r.getOwnerUserId())) {
                continue;
            }
            switch (GameCard.Zone.valueOf(r.getZone())) {
                case LIBRARY -> lib++;
                case HAND -> hand++;
                case GRAVEYARD -> gy++;
                case EXILE -> exile++;
                case COMMAND -> cmd++;
                case BATTLEFIELD -> bf++;
                default -> { }
            }
        }
        return new PlayerView(
                gp.getUserId(), username, gp.getSeat(), gp.getLifeTotal(), gp.isConnected(), gp.getStatus(),
                gp.getCommanderDamage(), gp.getCounters(), lib, hand, gy, exile, cmd, bf);
    }

    public GameCardView cardView(GameCardRow r, long viewerId) {
        boolean revealed = parseLongList(r.getRevealedTo()).contains(viewerId);
        boolean owner = r.getOwnerUserId() != null && r.getOwnerUserId() == viewerId;
        GameCard.Zone zone = GameCard.Zone.valueOf(r.getZone());
        boolean faceDown = Boolean.TRUE.equals(r.getFaceDown());

        boolean showIdentity = switch (zone) {
            case LIBRARY -> revealed;
            case HAND -> owner || revealed;
            default -> !faceDown || owner || revealed;
        };

        boolean isToken = Boolean.TRUE.equals(r.getToken());
        CardIdentity identity = showIdentity ? new CardIdentity(
                r.getOracleCardId(), r.getPrintingId(), r.getCustomArtId(),
                r.getName(), r.getDisplayName(), r.getTypeLine(), r.getManaCost(), toDouble(r.getManaValue()),
                r.getOracleText(), r.getColorIdentity(),
                r.getImageSmall(), r.getImageNormal(), r.getImageLarge(),
                isToken, r.getTokenPt(), r.getTokenColors()) : null;

        return new GameCardView(
                r.getId(), r.getOwnerUserId(), r.getControllerUserId(), r.getZone(),
                r.getPosition() == null ? 0 : r.getPosition(),
                toDouble(r.getX()), toDouble(r.getY()),
                Boolean.TRUE.equals(r.getTapped()), faceDown,
                r.getRotation() == null ? 0 : r.getRotation(),
                parseIntMap(r.getCounters()),
                identity);
    }

    public Map<Long, GameCardRow> rowsById(long gameId) {
        Map<Long, GameCardRow> map = new LinkedHashMap<>();
        for (GameCardRow r : gameCards.loadRows(gameId)) {
            map.put(r.getId(), r);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<Long> participantUserIds(long gameId) {
        Game game = games.findById(gameId).orElse(null);
        if (game == null) {
            return List.of();
        }
        return roomPlayers.findByRoomIdOrderByJoinedAt(game.getRoomId()).stream()
                .filter(p -> !RoomPlayer.Status.LEFT.name().equals(p.getStatus()))
                .map(RoomPlayer::getUserId)
                .distinct()
                .toList();
    }

    private Map<String, Integer> parseIntMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Double toDouble(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }
}
