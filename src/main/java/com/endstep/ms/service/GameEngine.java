package com.endstep.ms.service;

import com.endstep.ms.entity.CardOracle;
import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.GameCard;
import com.endstep.ms.entity.GameEvent;
import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.CardOracleRepository;
import com.endstep.ms.repository.GameCardRepository;
import com.endstep.ms.repository.GameEventRepository;
import com.endstep.ms.repository.GamePlayerRepository;
import com.endstep.ms.repository.GameRepository;
import com.endstep.ms.repository.UserRepository;
import com.endstep.ms.service.game.EngineResult;
import com.endstep.ms.service.game.GameActionMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Autoridade do estado da partida (endstep.txt secao 31). Mesa virtual: move
 * cartas, nao resolve regras. Cada acao e serializada por jogo e roda numa
 * transacao completa (lock -> commit -> unlock).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class GameEngine {
    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final GameCardRepository gameCards;
    private final GameEventRepository gameEvents;
    private final UserRepository users;
    private final CardOracleRepository cardOracles;
    private final TransactionTemplate tx;

    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public GameEngine(GameRepository games, GamePlayerRepository gamePlayers, GameCardRepository gameCards,
                      GameEventRepository gameEvents, UserRepository users, CardOracleRepository cardOracles,
                      PlatformTransactionManager txManager) {
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.gameCards = gameCards;
        this.gameEvents = gameEvents;
        this.users = users;
        this.cardOracles = cardOracles;
        this.tx = new TransactionTemplate(txManager);
    }

    public EngineResult apply(long gameId, long actorUserId, GameActionMessage msg) {
        Object lock = locks.computeIfAbsent(gameId, k -> new Object());
        synchronized (lock) {
            return tx.execute(status -> doApply(gameId, actorUserId, msg));
        }
    }

    private EngineResult doApply(long gameId, long actorUserId, GameActionMessage msg) {
        Game game = games.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Partida nao encontrada"));
        if (!Game.Status.ACTIVE.name().equals(game.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Partida nao esta ativa");
        }
        List<GamePlayer> players = gamePlayers.findByGameIdOrderBySeat(gameId);
        GamePlayer actor = players.stream().filter(p -> p.getUserId() == actorUserId).findFirst().orElse(null);
        Map<Long, String> names = users.findAllById(players.stream().map(GamePlayer::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        String me = names.getOrDefault(actorUserId, "user#" + actorUserId);

        EngineResult res = new EngineResult().eventType(msg.type());
        String type = msg.type() == null ? "" : msg.type().toUpperCase();

        switch (type) {
            case "DRAW" -> draw(game, requireActor(actor), res, me, msg.getInt("count", 1));
            case "DRAW_HAND" -> draw(game, requireActor(actor), res, me, msg.getInt("count", 7));
            case "MULLIGAN" -> mulligan(game, requireActor(actor), res, me);
            case "PLAY_CARD" -> playCard(game, actorUserId, res, me, msg);
            case "MOVE_CARD" -> moveCard(game, actorUserId, res, me, names, msg);
            case "SET_POSITION" -> setPosition(actorUserId, res, msg);
            case "SET_TAPPED" -> setTapped(actorUserId, res, me, msg);
            case "UNTAP_ALL" -> untapAll(gameId, requireActor(actor), res, me);
            case "ROTATE" -> rotate(actorUserId, res, msg);
            case "SET_FACE_DOWN" -> setFaceDown(actorUserId, res, me, msg);
            case "CARD_COUNTER" -> cardCounter(actorUserId, res, me, msg);
            case "PLAYER_COUNTER" -> playerCounter(players, actorUserId, res, names, msg);
            case "CHANGE_LIFE" -> changeLife(players, actorUserId, res, names, msg);
            case "COMMANDER_DAMAGE" -> commanderDamage(players, res, names, msg);
            case "SHUFFLE_LIBRARY" -> shuffleLibrary(gameId, requireActor(actor), res, me);
            case "PEEK_LIBRARY" -> peekLibrary(gameId, requireActor(actor), actorUserId, res, me, msg.getInt("count", 1));
            case "SEARCH_TO_HAND" -> searchToHand(game, actorUserId, res, me, msg);
            case "MILL" -> mill(game, actorUserId, res, me, msg.getInt("count", 1));
            case "LOOK_TOP" -> lookTop(game, actorUserId, res, me, msg);
            case "LOOK_TOP_RESOLVE" -> lookTopResolve(game, actorUserId, res, me, msg);
            case "CASCADE", "DISCOVER" -> cascade(game, requireActor(actor), actorUserId, players, res, me, msg);
            case "CREATE_TOKEN" -> createToken(game, actorUserId, res, me, msg);
            case "COPY_CARD" -> copyCard(game, actorUserId, res, me, msg);
            case "ADD_CARD" -> addCard(game, actorUserId, res, me, msg);
            case "REVEAL_CARD" -> revealCard(players, actorUserId, res, me, msg, true);
            case "HIDE_CARD" -> revealCard(players, actorUserId, res, me, msg, false);
            case "PASS_TURN" -> passTurn(game, players, res, names);
            case "SET_PHASE" -> setPhase(game, players, res, me, msg);
            case "SURRENDER" -> surrender(game, players, requireActor(actor), res, me);
            default -> throw new ResponseStatusException(BAD_REQUEST, "Acao desconhecida: " + msg.type());
        }

        long seq = game.getLastSequence() + 1;
        game.setLastSequence(seq);
        games.save(game);

        GameEvent ev = new GameEvent();
        ev.setGameId(gameId);
        ev.setSequenceNumber(seq);
        ev.setActorUserId(actorUserId);
        ev.setEventType(res.eventType() == null ? type : res.eventType());
        ev.setLogLine(res.logLine());
        gameEvents.save(ev);

        return res.sequence(seq);
    }

    private GamePlayer requireActor(GamePlayer actor) {
        if (actor == null) {
            throw new ResponseStatusException(FORBIDDEN, "Voce nao e um jogador desta partida");
        }
        return actor;
    }

    private List<GameCard> zone(Long gameId, Long ownerId, GameCard.Zone z) {
        List<GameCard> list = new ArrayList<>(
                gameCards.findByGameIdAndZoneAndOwnerUserId(gameId, z.name(), ownerId));
        list.sort(Comparator.comparingInt(GameCard::getPosition).thenComparing(GameCard::getId));
        return list;
    }

    private void reindex(List<GameCard> list) {
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setPosition(i);
        }
        gameCards.saveAll(list);
    }

    private GameCard card(long cardId, long gameId) {
        GameCard gc = gameCards.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        if (!gc.getGameId().equals(gameId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Carta de outra partida");
        }
        return gc;
    }

    private void requireControl(GameCard gc, long userId) {
        if (!gc.getControllerUserId().equals(userId) && !gc.getOwnerUserId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Voce nao controla essa carta");
        }
    }

    private void requireOwner(GameCard gc, long userId) {
        if (!gc.getOwnerUserId().equals(userId)) {
            throw new ResponseStatusException(FORBIDDEN, "Essa carta nao e sua");
        }
    }

    private void draw(Game game, GamePlayer actor, EngineResult res, String me, int count) {
        int n = Math.max(1, Math.min(count, 60));
        List<GameCard> lib = zone(game.getId(), actor.getUserId(), GameCard.Zone.LIBRARY);
        List<GameCard> hand = zone(game.getId(), actor.getUserId(), GameCard.Zone.HAND);
        int drawn = 0;
        for (int i = 0; i < n && !lib.isEmpty(); i++) {
            GameCard top = lib.remove(0);
            top.setZone(GameCard.Zone.HAND.name());
            top.getRevealedTo().clear();
            hand.add(top);
            res.card(top.getId());
            drawn++;
        }
        reindex(lib);
        reindex(hand);
        res.player(actor.getUserId());
        res.eventType("CARD_DRAWN").logLine(me + " comprou " + drawn + (drawn == 1 ? " carta" : " cartas"));
    }

    private void mulligan(Game game, GamePlayer actor, EngineResult res, String me) {
        List<GameCard> hand = zone(game.getId(), actor.getUserId(), GameCard.Zone.HAND);
        List<GameCard> lib = zone(game.getId(), actor.getUserId(), GameCard.Zone.LIBRARY);
        for (GameCard c : hand) {
            c.setZone(GameCard.Zone.LIBRARY.name());
            c.getRevealedTo().clear();
            lib.add(c);
            res.card(c.getId());
        }
        Collections.shuffle(lib);
        reindex(lib);
        List<GameCard> newHand = new ArrayList<>();
        for (int i = 0; i < 7 && !lib.isEmpty(); i++) {
            GameCard top = lib.remove(0);
            top.setZone(GameCard.Zone.HAND.name());
            newHand.add(top);
            res.card(top.getId());
        }
        reindex(lib);
        reindex(newHand);
        res.player(actor.getUserId());
        res.eventType("LIBRARY_SHUFFLED").logLine(me + " fez mulligan (nova mao de 7)");
    }

    private void playCard(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        GameCard gc = card(msg.getLong("cardId", -1), game.getId());
        requireControl(gc, actorUserId);
        String fromZone = gc.getZone();
        gc.setZone(GameCard.Zone.BATTLEFIELD.name());
        gc.setFaceDown(msg.getBool("faceDown", false));
        gc.setTapped(false);
        Double x = msg.getDoubleOrNull("x");
        Double y = msg.getDoubleOrNull("y");
        gc.setX(x == null ? BigDecimal.valueOf(0.45) : BigDecimal.valueOf(clamp01(x)));
        gc.setY(y == null ? BigDecimal.valueOf(0.55) : BigDecimal.valueOf(clamp01(y)));
        List<GameCard> bf = zone(game.getId(), gc.getOwnerUserId(), GameCard.Zone.BATTLEFIELD);
        if (!bf.contains(gc)) {
            bf.add(gc);
        }
        reindex(bf);
        if (GameCard.Zone.HAND.name().equals(fromZone) || GameCard.Zone.LIBRARY.name().equals(fromZone)) {
            res.player(gc.getOwnerUserId());
        }
        res.card(gc.getId());
        res.eventType("CARD_PLAYED").logLine(me + " jogou uma carta no campo");
    }

    private void moveCard(Game game, long actorUserId, EngineResult res, String me, Map<Long, String> names,
                          GameActionMessage msg) {
        GameCard gc = card(msg.getLong("cardId", -1), game.getId());
        requireControl(gc, actorUserId);
        GameCard.Zone to;
        try {
            to = GameCard.Zone.valueOf(msg.getString("toZone"));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Zona invalida");
        }
        String fromZone = gc.getZone();
        String placement = msg.getString("placement");

        if (gc.isToken() && GameCard.Zone.BATTLEFIELD.name().equals(fromZone) && to != GameCard.Zone.BATTLEFIELD) {
            gameCards.delete(gc);
            try {
                List<GameCard> src = zone(game.getId(), gc.getOwnerUserId(), GameCard.Zone.BATTLEFIELD);
                src.remove(gc);
                reindex(src);
            } catch (Exception ignored) {
            }
            res.removed(gc.getId()).eventType("CARD_MOVED").logLine(me + " removeu uma ficha");
            return;
        }
        gc.setZone(to.name());
        if (to != GameCard.Zone.BATTLEFIELD) {
            gc.setX(null);
            gc.setY(null);
            gc.setTapped(false);
        } else if (gc.getX() == null || gc.getY() == null) {
            gc.setX(BigDecimal.valueOf(0.42));
            gc.setY(BigDecimal.valueOf(0.35));
        }
        if (to == GameCard.Zone.LIBRARY || to == GameCard.Zone.HAND) {
            gc.getRevealedTo().clear();
        }
        List<GameCard> dest = zone(game.getId(), gc.getOwnerUserId(), to);
        dest.remove(gc);
        if (to == GameCard.Zone.LIBRARY && "BOTTOM".equalsIgnoreCase(placement)) {
            dest.add(gc);
        } else if (to == GameCard.Zone.LIBRARY) {
            dest.add(0, gc);
        } else {
            dest.add(gc);
        }
        reindex(dest);

        try {
            List<GameCard> src = zone(game.getId(), gc.getOwnerUserId(), GameCard.Zone.valueOf(fromZone));
            src.remove(gc);
            reindex(src);
        } catch (Exception ignored) {
        }
        boolean hiddenNow = to == GameCard.Zone.LIBRARY || to == GameCard.Zone.HAND;
        if (hiddenNow) {
            res.player(gc.getOwnerUserId());
        }
        res.card(gc.getId());
        res.eventType("CARD_MOVED").logLine(me + " moveu uma carta para " + ptZone(to));
    }

    private void setPosition(long actorUserId, EngineResult res, GameActionMessage msg) {
        GameCard gc = gameCards.findById(msg.getLong("cardId", -1))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        requireControl(gc, actorUserId);
        Double x = msg.getDoubleOrNull("x");
        Double y = msg.getDoubleOrNull("y");
        if (x != null) {
            gc.setX(BigDecimal.valueOf(clamp01(x)));
        }
        if (y != null) {
            gc.setY(BigDecimal.valueOf(clamp01(y)));
        }
        gameCards.save(gc);
        res.card(gc.getId()).eventType("CARD_MOVED").logLine(null);
    }

    private void setTapped(long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        GameCard gc = gameCards.findById(msg.getLong("cardId", -1))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        requireControl(gc, actorUserId);
        boolean tapped = msg.getBool("tapped", !gc.isTapped());
        gc.setTapped(tapped);
        gameCards.save(gc);
        res.card(gc.getId()).eventType("CARD_UPDATED")
                .logLine(me + (tapped ? " virou uma carta" : " desvirou uma carta"));
    }

    private void untapAll(long gameId, GamePlayer actor, EngineResult res, String me) {
        List<GameCard> bf = zone(gameId, actor.getUserId(), GameCard.Zone.BATTLEFIELD);
        for (GameCard c : bf) {
            if (c.isTapped()) {
                c.setTapped(false);
                res.card(c.getId());
            }
        }
        gameCards.saveAll(bf);
        res.eventType("CARD_UPDATED").logLine(me + " desvirou todas as permanentes");
    }

    private void rotate(long actorUserId, EngineResult res, GameActionMessage msg) {
        GameCard gc = gameCards.findById(msg.getLong("cardId", -1))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        requireControl(gc, actorUserId);
        gc.setRotation(((msg.getInt("rotation", gc.getRotation() + 180)) % 360 + 360) % 360);
        gameCards.save(gc);
        res.card(gc.getId()).eventType("CARD_UPDATED").logLine(null);
    }

    private void setFaceDown(long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        GameCard gc = gameCards.findById(msg.getLong("cardId", -1))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        requireControl(gc, actorUserId);
        boolean fd = msg.getBool("faceDown", !gc.isFaceDown());
        gc.setFaceDown(fd);
        if (fd) {
            gc.getRevealedTo().clear();
        }
        gameCards.save(gc);
        res.card(gc.getId()).eventType("CARD_UPDATED")
                .logLine(me + (fd ? " virou uma carta para baixo" : " virou uma carta para cima"));
    }

    private void cardCounter(long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        GameCard gc = gameCards.findById(msg.getLong("cardId", -1))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        requireControl(gc, actorUserId);
        String kind = safeKind(msg.getString("kind"));
        int delta = msg.getInt("delta", 1);
        Map<String, Integer> c = gc.getCounters();
        int now = c.getOrDefault(kind, 0) + delta;
        if (now == 0) {
            c.remove(kind);
        } else {
            c.put(kind, now);
        }
        gameCards.save(gc);
        res.card(gc.getId()).eventType("COUNTER_CHANGED")
                .logLine(me + " ajustou marcador " + kind + " (" + now + ")");
    }

    private void playerCounter(List<GamePlayer> players, long actorUserId, EngineResult res,
                               Map<Long, String> names, GameActionMessage msg) {
        long targetId = msg.getLong("targetUserId", actorUserId);
        GamePlayer gp = players.stream().filter(p -> p.getUserId() == targetId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Jogador alvo invalido"));
        String kind = safeKind(msg.getString("kind"));
        int delta = msg.getInt("delta", 1);
        int now = gp.getCounters().getOrDefault(kind, 0) + delta;
        now = Math.max(0, now);
        if ("poison".equals(kind)) {
            now = Math.min(now, 10);
        }
        if (now == 0) {
            gp.getCounters().remove(kind);
        } else {
            gp.getCounters().put(kind, now);
        }
        gamePlayers.save(gp);
        String extra = "poison".equals(kind) && now >= 10 ? " (perde a partida)" : "";
        res.player(gp.getUserId()).eventType("PLAYER_COUNTER_CHANGED")
                .logLine(names.getOrDefault(targetId, "?") + ": " + kind + " = " + now + extra);
    }

    private void changeLife(List<GamePlayer> players, long actorUserId, EngineResult res,
                            Map<Long, String> names, GameActionMessage msg) {
        long targetId = msg.getLong("targetUserId", actorUserId);
        GamePlayer gp = players.stream().filter(p -> p.getUserId() == targetId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Jogador alvo invalido"));
        Long absolute = msg.getLongOrNull("absolute");
        int newLife = absolute != null ? absolute.intValue() : gp.getLifeTotal() + msg.getInt("delta", 0);
        gp.setLifeTotal(newLife);
        gamePlayers.save(gp);
        res.player(gp.getUserId()).eventType("LIFE_CHANGED")
                .logLine(names.getOrDefault(targetId, "?") + " agora com " + newLife + " de vida");
    }

    private void commanderDamage(List<GamePlayer> players, EngineResult res, Map<Long, String> names,
                                 GameActionMessage msg) {
        long fromId = msg.getLong("fromUserId", -1);
        long toId = msg.getLong("toUserId", -1);
        int delta = msg.getInt("delta", 1);
        GamePlayer to = players.stream().filter(p -> p.getUserId() == toId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Alvo invalido"));
        String key = String.valueOf(fromId);
        int now = Math.max(0, to.getCommanderDamage().getOrDefault(key, 0) + delta);
        to.getCommanderDamage().put(key, now);
        to.setLifeTotal(to.getLifeTotal() - delta);
        gamePlayers.save(to);
        res.player(to.getUserId()).eventType("COMMANDER_DAMAGE_CHANGED")
                .logLine(names.getOrDefault(toId, "?") + " levou " + delta + " de dano de commander de "
                        + names.getOrDefault(fromId, "?") + " (total " + now + ")");
    }

    private void shuffleLibrary(long gameId, GamePlayer actor, EngineResult res, String me) {
        List<GameCard> lib = zone(gameId, actor.getUserId(), GameCard.Zone.LIBRARY);
        Collections.shuffle(lib);
        for (GameCard c : lib) {
            c.getRevealedTo().clear();
        }
        reindex(lib);
        for (GameCard c : lib) {
            res.card(c.getId());
        }
        res.eventType("LIBRARY_SHUFFLED").logLine(me + " embaralhou a biblioteca");
    }

    private void peekLibrary(long gameId, GamePlayer actor, long actorUserId, EngineResult res, String me, int count) {
        List<GameCard> lib = zone(gameId, actor.getUserId(), GameCard.Zone.LIBRARY);
        int n = Math.max(1, Math.min(count, lib.size()));
        for (int i = 0; i < n; i++) {
            GameCard c = lib.get(i);
            if (!c.getRevealedTo().contains(actorUserId)) {
                c.getRevealedTo().add(actorUserId);
            }
            res.card(c.getId());
        }
        gameCards.saveAll(lib.subList(0, n));
        res.eventType("CARD_UPDATED").logLine(me + " olhou o topo da biblioteca (" + n + ")");
    }

    private void mill(Game game, long actorUserId, EngineResult res, String me, int count) {
        List<GameCard> lib = zone(game.getId(), actorUserId, GameCard.Zone.LIBRARY);
        int n = Math.max(1, Math.min(count, lib.size()));
        List<GameCard> gy = zone(game.getId(), actorUserId, GameCard.Zone.GRAVEYARD);
        for (int i = 0; i < n; i++) {
            GameCard c = lib.remove(0);
            c.setZone(GameCard.Zone.GRAVEYARD.name());
            c.getRevealedTo().clear();
            gy.add(c);
            res.card(c.getId());
        }
        reindex(lib);
        reindex(gy);
        res.player(actorUserId).eventType("CARD_MOVED")
                .logLine(me + " milou " + n + (n == 1 ? " carta" : " cartas") + " para o cemitério");
    }

    private void lookTop(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        int count = Math.max(1, msg.getInt("count", 1));
        String mode = msg.getString("mode");
        mode = mode == null ? "free" : mode.toLowerCase();
        List<GameCard> lib = zone(game.getId(), actorUserId, GameCard.Zone.LIBRARY);
        int n = Math.min(count, lib.size());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            GameCard c = lib.get(i);
            if (!c.getRevealedTo().contains(actorUserId)) {
                c.getRevealedTo().add(actorUserId);
            }
            ids.add(c.getId());
            res.card(c.getId());
        }
        gameCards.saveAll(lib.subList(0, n));
        String verb = "surveil".equals(mode) ? "vigiou" : "scry";
        res.eventType("CARD_UPDATED").logLine(me + " " + verb + " " + n);
        res.actorNotice(Map.of("kind", "LOOKTOP", "mode", mode, "cardIds", ids));
    }

    @SuppressWarnings("unchecked")
    private void lookTopResolve(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        Object decRaw = msg.payloadOrEmpty().get("decisions");
        if (!(decRaw instanceof List<?> decisions)) {
            return;
        }
        List<GameCard> lib = zone(game.getId(), actorUserId, GameCard.Zone.LIBRARY);
        List<GameCard> gy = zone(game.getId(), actorUserId, GameCard.Zone.GRAVEYARD);

        List<GameCard> newTop = new ArrayList<>();
        List<GameCard> toBottom = new ArrayList<>();
        int toGy = 0, toBot = 0, toTop = 0;
        for (Object o : decisions) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            long cardId = ((Number) m.get("cardId")).longValue();
            String dest = String.valueOf(m.get("dest"));
            GameCard c = lib.stream().filter(x -> x.getId() == cardId).findFirst().orElse(null);
            if (c == null) {
                continue;
            }
            lib.remove(c);
            c.getRevealedTo().clear();
            res.card(c.getId());
            switch (dest) {
                case "GRAVEYARD" -> {
                    c.setZone(GameCard.Zone.GRAVEYARD.name());
                    gy.add(c);
                    toGy++;
                }
                case "BOTTOM" -> {
                    toBottom.add(c);
                    toBot++;
                }
                default -> {
                    newTop.add(c);
                    toTop++;
                }
            }
        }

        List<GameCard> finalLib = new ArrayList<>(newTop);
        finalLib.addAll(lib);
        finalLib.addAll(toBottom);
        reindex(finalLib);
        reindex(gy);
        res.player(actorUserId).eventType("CARD_UPDATED").logLine(
                me + " resolveu: " + toTop + " no topo, " + toBot + " no fundo, " + toGy + " no cemitério");
    }

    private void searchToHand(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        GameCard gc = card(msg.getLong("cardId", -1), game.getId());
        requireOwner(gc, actorUserId);
        if (!GameCard.Zone.LIBRARY.name().equals(gc.getZone())) {
            throw new ResponseStatusException(BAD_REQUEST, "A carta nao esta na biblioteca");
        }
        gc.setZone(GameCard.Zone.HAND.name());
        gc.getRevealedTo().clear();
        List<GameCard> hand = zone(game.getId(), actorUserId, GameCard.Zone.HAND);
        if (!hand.contains(gc)) {
            hand.add(gc);
        }
        reindex(hand);

        List<GameCard> lib = zone(game.getId(), actorUserId, GameCard.Zone.LIBRARY);
        lib.remove(gc);
        Collections.shuffle(lib);
        for (GameCard c : lib) {
            c.getRevealedTo().clear();
            res.card(c.getId());
        }
        reindex(lib);
        res.card(gc.getId()).player(actorUserId).eventType("CARD_MOVED")
                .logLine(me + " buscou uma carta na biblioteca");
    }

    private void createToken(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        String name = msg.getString("name");
        if (name == null || name.isBlank()) {
            name = "Ficha";
        }
        name = name.length() > 120 ? name.substring(0, 120) : name.trim();
        String pt = msg.getString("pt");
        pt = pt == null ? null : (pt.length() > 16 ? pt.substring(0, 16) : pt.trim());
        String colors = msg.getString("colors");
        colors = colors == null ? null : colors.toUpperCase().replaceAll("[^WUBRG]", "");
        String text = msg.getString("text");
        text = text == null || text.isBlank() ? null : text.trim();
        int count = Math.max(1, Math.min(msg.getInt("count", 1), 30));

        for (int i = 0; i < count; i++) {
            GameCard t = new GameCard();
            t.setGameId(game.getId());
            t.setOwnerUserId(actorUserId);
            t.setControllerUserId(actorUserId);
            t.setToken(true);
            t.setTokenName(name);
            t.setTokenPt(pt);
            t.setTokenColors(colors == null || colors.isBlank() ? null : colors);
            t.setTokenText(text);
            t.setZone(GameCard.Zone.BATTLEFIELD.name());
            t.setX(BigDecimal.valueOf(0.3 + (i % 5) * 0.09));
            t.setY(BigDecimal.valueOf(0.6));
            gameCards.save(t);
            res.card(t.getId());
        }
        res.player(actorUserId).eventType("CARD_PLAYED")
                .logLine(me + " criou " + count + (count == 1 ? " ficha " : " fichas ") + name
                        + (pt != null ? " " + pt : ""));
    }

    private void addCard(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        Long oracleCardId = msg.getLongOrNull("oracleCardId");
        String oracleId = msg.getString("oracleId");
        CardOracle oracle;
        if (oracleCardId != null) {
            oracle = cardOracles.findById(oracleCardId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        } else if (oracleId != null && !oracleId.isBlank()) {
            UUID u;
            try {
                u = UUID.fromString(oracleId.trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(BAD_REQUEST, "oracleId invalido");
            }
            oracle = cardOracles.findByOracleId(u)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        } else {
            throw new ResponseStatusException(BAD_REQUEST, "Informe oracleId ou oracleCardId");
        }

        GameCard.Zone zone = "HAND".equalsIgnoreCase(msg.getString("zone"))
                ? GameCard.Zone.HAND : GameCard.Zone.BATTLEFIELD;
        int count = Math.max(1, Math.min(msg.getInt("count", 1), 20));
        int basePos = zone == GameCard.Zone.HAND
                ? zone(game.getId(), actorUserId, GameCard.Zone.HAND).size() : 0;

        for (int i = 0; i < count; i++) {
            GameCard c = new GameCard();
            c.setGameId(game.getId());
            c.setOwnerUserId(actorUserId);
            c.setControllerUserId(actorUserId);
            c.setOracleCardId(oracle.getId());
            c.setToken(true);
            c.setZone(zone.name());
            if (zone == GameCard.Zone.BATTLEFIELD) {
                c.setX(BigDecimal.valueOf(0.3 + (i % 5) * 0.09));
                c.setY(BigDecimal.valueOf(0.62));
            } else {
                c.setPosition(basePos + i);
            }
            gameCards.save(c);
            res.card(c.getId());
        }
        res.player(actorUserId).eventType("CARD_PLAYED")
                .logLine(me + " adicionou " + count + "x " + oracle.getName()
                        + (zone == GameCard.Zone.HAND ? " à mão" : " ao campo"));
    }

    private void copyCard(Game game, long actorUserId, EngineResult res, String me, GameActionMessage msg) {
        GameCard src = card(msg.getLong("cardId", -1), game.getId());
        int count = Math.max(1, Math.min(msg.getInt("count", 1), 20));
        for (int i = 0; i < count; i++) {
            GameCard c = new GameCard();
            c.setGameId(game.getId());
            c.setOwnerUserId(actorUserId);
            c.setControllerUserId(actorUserId);
            c.setToken(true);
            c.setOracleCardId(src.getOracleCardId());
            c.setPrintingId(src.getPrintingId());
            c.setCustomArtId(src.getCustomArtId());
            c.setTokenName(src.getTokenName());
            c.setTokenPt(src.getTokenPt());
            c.setTokenColors(src.getTokenColors());
            c.setTokenText(src.getTokenText());
            c.setFaceDown(src.isFaceDown());
            c.setZone(GameCard.Zone.BATTLEFIELD.name());
            BigDecimal sx = src.getX() == null ? BigDecimal.valueOf(0.45) : src.getX();
            BigDecimal sy = src.getY() == null ? BigDecimal.valueOf(0.5) : src.getY();
            c.setX(sx.add(BigDecimal.valueOf(0.05 * (i + 1))));
            c.setY(sy.add(BigDecimal.valueOf(0.05 * (i + 1))));
            c.getRevealedTo().addAll(src.getRevealedTo());
            gameCards.save(c);
            res.card(c.getId());
        }
        res.player(actorUserId).eventType("CARD_PLAYED")
                .logLine(me + " copiou uma permanente (" + count + (count == 1 ? " cópia)" : " cópias)"));
    }

    private void cascade(Game game, GamePlayer actor, long actorUserId, List<GamePlayer> players,
                         EngineResult res, String me, GameActionMessage msg) {
        boolean inclusive = msg.getBool("inclusive", false);
        String kw = inclusive ? "DISCOVER" : "CASCADE";
        String cmp = inclusive ? "VM <= " : "VM < ";

        BigDecimal threshold;
        Long spellCardId = msg.getLongOrNull("cardId");
        if (spellCardId != null) {
            GameCard spell = card(spellCardId, game.getId());
            BigDecimal mv = cardOracles.findById(spell.getOracleCardId())
                    .map(CardOracle::getManaValue).orElse(null);
            threshold = mv == null ? BigDecimal.ZERO : mv;
        } else {
            Double raw = msg.getDoubleOrNull("maxMv");
            threshold = BigDecimal.valueOf(raw == null ? 0 : raw);
        }

        List<GameCard> lib = zone(game.getId(), actorUserId, GameCard.Zone.LIBRARY);
        if (lib.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Grimorio vazio");
        }
        Map<Long, CardOracle> oracles = cardOracles.findAllById(
                        lib.stream().map(GameCard::getOracleCardId).distinct().toList())
                .stream().collect(Collectors.toMap(CardOracle::getId, o -> o, (a, b) -> a));
        List<Long> everyone = players.stream().map(GamePlayer::getUserId).toList();

        List<GameCard> exiled = new ArrayList<>();
        GameCard hit = null;
        for (GameCard c : lib) {
            CardOracle o = oracles.get(c.getOracleCardId());
            String tl = o != null && o.getTypeLine() != null ? o.getTypeLine().toLowerCase() : "";
            boolean isLand = tl.contains("land");
            BigDecimal mv = o != null && o.getManaValue() != null ? o.getManaValue() : BigDecimal.ZERO;

            c.setZone(GameCard.Zone.EXILE.name());
            c.getRevealedTo().clear();
            c.getRevealedTo().addAll(everyone);
            exiled.add(c);
            res.card(c.getId());
            int c2 = mv.compareTo(threshold);
            if (!isLand && (inclusive ? c2 <= 0 : c2 < 0)) {
                hit = c;
                break;
            }
        }

        List<GameCard> rest = new ArrayList<>(exiled);
        if (hit != null) {
            rest.remove(hit);
        }
        Collections.shuffle(rest);

        java.util.Set<Long> exiledIds = exiled.stream().map(GameCard::getId).collect(Collectors.toSet());
        List<GameCard> newLib = lib.stream().filter(c -> !exiledIds.contains(c.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        for (GameCard c : rest) {
            c.setZone(GameCard.Zone.LIBRARY.name());
            c.getRevealedTo().clear();
            newLib.add(c);
        }
        reindex(newLib);
        reindex(zone(game.getId(), actorUserId, GameCard.Zone.EXILE));
        gameCards.saveAll(exiled);

        res.player(actorUserId).eventType(kw);
        String limStr = threshold.stripTrailingZeros().toPlainString();
        String verb = inclusive ? " descobriu (" : " cascateou (";
        if (hit != null) {
            CardOracle ho = oracles.get(hit.getOracleCardId());
            String hitName = ho != null && ho.getName() != null ? ho.getName() : "uma carta";
            res.logLine(me + verb + cmp + limStr + "): revelou " + exiled.size()
                    + (exiled.size() == 1 ? " carta" : " cartas") + " e achou " + hitName);
            res.actorNotice(Map.of(
                    "kind", kw,
                    "hitCardId", hit.getId(),
                    "hitName", hitName,
                    "exiledCount", exiled.size()));
        } else {
            res.logLine(me + verb + cmp + limStr + "): revelou " + exiled.size()
                    + (exiled.size() == 1 ? " carta" : " cartas")
                    + ", nenhuma elegivel — tudo ao fundo do grimorio");
            res.actorNotice(Map.of("kind", kw, "exiledCount", exiled.size()));
        }
    }

    private void revealCard(List<GamePlayer> players, long actorUserId, EngineResult res, String me,
                            GameActionMessage msg, boolean reveal) {
        GameCard gc = gameCards.findById(msg.getLong("cardId", -1))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Carta nao encontrada"));
        requireOwner(gc, actorUserId);
        gc.getRevealedTo().clear();
        if (reveal) {
            for (GamePlayer p : players) {
                gc.getRevealedTo().add(p.getUserId());
            }
        }
        gameCards.save(gc);
        res.card(gc.getId()).eventType("CARD_UPDATED")
                .logLine(me + (reveal ? " revelou uma carta" : " escondeu uma carta"));
    }

    private void passTurn(Game game, List<GamePlayer> players, EngineResult res, Map<Long, String> names) {
        List<Integer> seats = players.stream()
                .filter(p -> GamePlayer.Status.PLAYING.name().equals(p.getStatus()))
                .map(GamePlayer::getSeat)
                .sorted()
                .toList();
        if (seats.isEmpty()) {
            res.turnChanged(true).eventType("TURN_CHANGED").logLine(null);
            return;
        }
        int cur = game.getActiveSeat();
        Integer next = seats.stream().filter(sn -> sn > cur).findFirst().orElse(null);
        if (next == null) {
            next = seats.get(0);
            game.setTurnNumber(game.getTurnNumber() + 1);
        }
        game.setActiveSeat(next);
        game.setPhase(Game.Phase.UNTAP.name());
        final int ns = next;
        Long nextUser = players.stream().filter(p -> p.getSeat() == ns).map(GamePlayer::getUserId)
                .findFirst().orElse(null);
        res.turnChanged(true).eventType("TURN_CHANGED")
                .logLine("Turno " + game.getTurnNumber() + " — vez de " + names.getOrDefault(nextUser, "?"));
    }

    private void setPhase(Game game, List<GamePlayer> players, EngineResult res, String me, GameActionMessage msg) {
        Game.Phase phase;
        try {
            phase = Game.Phase.valueOf(msg.getString("phase"));
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Fase invalida");
        }
        game.setPhase(phase.name());
        res.turnChanged(true).eventType("TURN_CHANGED").logLine(me + " passou para " + phase.name());
    }

    private void surrender(Game game, List<GamePlayer> players, GamePlayer actor, EngineResult res, String me) {
        actor.setStatus(GamePlayer.Status.LOST.name());
        gamePlayers.save(actor);
        res.player(actor.getUserId());
        long alive = players.stream().filter(p -> GamePlayer.Status.PLAYING.name().equals(p.getStatus())).count();
        if (alive <= 1) {
            game.setStatus(Game.Status.FINISHED.name());
            game.setFinishedAt(java.time.Instant.now());
        }
        res.eventType("PLAYER_LEFT").logLine(me + " desistiu da partida");
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static String safeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "generic";
        }
        String k = kind.trim();
        return k.length() > 24 ? k.substring(0, 24) : k;
    }

    private static String ptZone(GameCard.Zone z) {
        return switch (z) {
            case LIBRARY -> "a biblioteca";
            case HAND -> "a mao";
            case BATTLEFIELD -> "o campo";
            case GRAVEYARD -> "o cemiterio";
            case EXILE -> "o exilio";
            case COMMAND -> "a command zone";
            case STACK -> "a pilha";
        };
    }
}
