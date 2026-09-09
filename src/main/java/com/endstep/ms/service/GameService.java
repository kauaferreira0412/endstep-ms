package com.endstep.ms.service;

import com.endstep.ms.entity.DeckCard;
import com.endstep.ms.entity.FormatEntity;
import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.GameCard;
import com.endstep.ms.entity.GamePlayer;
import com.endstep.ms.entity.Room;
import com.endstep.ms.entity.RoomPlayer;
import com.endstep.ms.repository.DeckCardRepository;
import com.endstep.ms.repository.FormatRepository;
import com.endstep.ms.repository.GameCardRepository;
import com.endstep.ms.repository.GamePlayerRepository;
import com.endstep.ms.repository.GameRepository;
import com.endstep.ms.repository.RoomPlayerRepository;
import com.endstep.ms.repository.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Cria a partida a partir da sala: materializa game_cards dos decks (endstep.txt secao 60).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class GameService {
    private final RoomRepository rooms;
    private final RoomPlayerRepository roomPlayers;
    private final GameRepository games;
    private final GamePlayerRepository gamePlayers;
    private final GameCardRepository gameCards;
    private final DeckCardRepository deckCards;
    private final FormatRepository formats;
    private final GameEventPublisher publisher;

    public GameService(RoomRepository rooms, RoomPlayerRepository roomPlayers, GameRepository games,
                       GamePlayerRepository gamePlayers, GameCardRepository gameCards,
                       DeckCardRepository deckCards, FormatRepository formats, GameEventPublisher publisher) {
        this.rooms = rooms;
        this.roomPlayers = roomPlayers;
        this.games = games;
        this.gamePlayers = gamePlayers;
        this.gameCards = gameCards;
        this.deckCards = deckCards;
        this.formats = formats;
        this.publisher = publisher;
    }

    @Transactional
    public Long startFromRoom(Long hostUserId, String roomCode) {
        Room room = rooms.findByRoomCode(roomCode)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sala nao encontrada"));
        if (!room.getHostUserId().equals(hostUserId)) {
            throw new ResponseStatusException(FORBIDDEN, "So o host inicia a partida");
        }
        if (Room.Status.IN_GAME.name().equals(room.getStatus()) && room.getCurrentGameId() != null) {
            return room.getCurrentGameId();
        }

        List<RoomPlayer> players = roomPlayers.findByRoomIdOrderByJoinedAt(room.getId()).stream()
                .filter(p -> RoomPlayer.Role.PLAYER.name().equals(p.getRole())
                        && !RoomPlayer.Status.LEFT.name().equals(p.getStatus()))
                .toList();
        if (players.size() < 2) {
            throw new ResponseStatusException(CONFLICT, "Precisa de pelo menos 2 jogadores");
        }
        for (RoomPlayer p : players) {
            if (p.getDeckId() == null) {
                throw new ResponseStatusException(BAD_REQUEST, "Todos os jogadores precisam escolher um deck");
            }
            long playable = deckCards.findByDeckId(p.getDeckId()).stream()
                    .filter(dc -> DeckCard.Section.COMMANDER.name().equals(dc.getSection())
                            || DeckCard.Section.MAINBOARD.name().equals(dc.getSection()))
                    .mapToLong(DeckCard::getQuantity)
                    .sum();
            if (playable == 0) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "O deck de um dos jogadores nao tem cartas. Adicione cartas ao deck em \"Decks\" e tente de novo.");
            }
        }
        FormatEntity fmt = formats.findById(room.getFormat())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Formato invalido"));

        Game game = new Game();
        game.setRoomId(room.getId());
        game.setFormat(room.getFormat());
        game.setStatus(Game.Status.ACTIVE.name());
        game.setCreatedBy(hostUserId);
        game.setStartedAt(Instant.now());
        games.save(game);

        int seat = 0;
        for (RoomPlayer rp : players) {
            GamePlayer gp = new GamePlayer();
            gp.setGameId(game.getId());
            gp.setUserId(rp.getUserId());
            gp.setSeat(seat);
            gp.setDeckId(rp.getDeckId());
            gp.setLifeTotal(fmt.getDefaultLife());
            gamePlayers.save(gp);

            materializeDeck(game.getId(), rp.getUserId(), rp.getDeckId(), fmt.isUsesCommandZone());

            rp.setSeat(seat);
            roomPlayers.save(rp);
            seat++;
        }

        room.setStatus(Room.Status.IN_GAME.name());
        room.setCurrentGameId(game.getId());
        rooms.save(room);
        return game.getId();
    }

    @Transactional
    public Long sitDown(Long userId, String roomCode) {
        Room room = rooms.findByRoomCode(roomCode)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sala nao encontrada"));
        RoomPlayer rp = roomPlayers.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Voce nao esta na sala"));

        long activePlayers = roomPlayers.findByRoomIdOrderByJoinedAt(room.getId()).stream()
                .filter(p -> RoomPlayer.Role.PLAYER.name().equals(p.getRole())
                        && !RoomPlayer.Status.LEFT.name().equals(p.getStatus())
                        && !p.getUserId().equals(userId))
                .count();
        if (activePlayers >= room.getMaxPlayers()) {
            throw new ResponseStatusException(CONFLICT, "Sala cheia (" + room.getMaxPlayers() + " jogadores)");
        }

        rp.setRole(RoomPlayer.Role.PLAYER.name());
        rp.setStatus(rp.getDeckId() != null ? RoomPlayer.Status.READY.name() : RoomPlayer.Status.JOINED.name());
        roomPlayers.save(rp);

        boolean running = Room.Status.IN_GAME.name().equals(room.getStatus()) && room.getCurrentGameId() != null;
        if (!running) {
            return null;
        }
        if (rp.getDeckId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Escolha um deck antes de entrar na partida");
        }
        Game game = games.findById(room.getCurrentGameId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Partida nao encontrada"));
        if (!Game.Status.ACTIVE.name().equals(game.getStatus())) {
            return game.getId();
        }
        if (gamePlayers.findByGameIdAndUserId(game.getId(), userId).isPresent()) {
            return game.getId();
        }
        FormatEntity fmt = formats.findById(game.getFormat())
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Formato invalido"));
        int seat = gamePlayers.findByGameIdOrderBySeat(game.getId()).stream()
                .mapToInt(GamePlayer::getSeat).max().orElse(-1) + 1;

        GamePlayer gp = new GamePlayer();
        gp.setGameId(game.getId());
        gp.setUserId(userId);
        gp.setSeat(seat);
        gp.setDeckId(rp.getDeckId());
        gp.setLifeTotal(fmt.getDefaultLife());
        gamePlayers.save(gp);
        materializeDeck(game.getId(), userId, rp.getDeckId(), fmt.isUsesCommandZone());

        rp.setSeat(seat);
        roomPlayers.save(rp);

        publisher.resyncAll(game.getId());
        return game.getId();
    }

    private void materializeDeck(Long gameId, Long userId, Long deckId, boolean usesCommandZone) {
        List<DeckCard> rows = deckCards.findByDeckId(deckId);
        List<GameCard> library = new ArrayList<>();
        List<GameCard> command = new ArrayList<>();

        for (DeckCard dc : rows) {
            String section = dc.getSection();
            boolean isCommander = DeckCard.Section.COMMANDER.name().equals(section);
            boolean isMain = DeckCard.Section.MAINBOARD.name().equals(section);
            if (!isCommander && !isMain) {
                continue;
            }
            for (int i = 0; i < dc.getQuantity(); i++) {
                GameCard gc = new GameCard();
                gc.setGameId(gameId);
                gc.setOwnerUserId(userId);
                gc.setControllerUserId(userId);
                gc.setOracleCardId(dc.getOracleCardId());
                gc.setPrintingId(dc.getPrintingId());
                gc.setCustomArtId(dc.getCustomArtId());
                if (isCommander && usesCommandZone) {
                    gc.setZone(GameCard.Zone.COMMAND.name());
                    command.add(gc);
                } else {
                    gc.setZone(GameCard.Zone.LIBRARY.name());
                    library.add(gc);
                }
            }
        }
        Collections.shuffle(library);
        for (int i = 0; i < library.size(); i++) {
            library.get(i).setPosition(i);
        }
        for (int i = 0; i < command.size(); i++) {
            command.get(i).setPosition(i);
        }
        gameCards.saveAll(library);
        gameCards.saveAll(command);
    }
}
