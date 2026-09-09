package com.endstep.ms.service;

import com.endstep.ms.dto.RoomDtos.ChooseDeckRequest;
import com.endstep.ms.dto.RoomDtos.CreateRoomRequest;
import com.endstep.ms.dto.RoomDtos.JoinRoomRequest;
import com.endstep.ms.dto.RoomDtos.RoomPlayerView;
import com.endstep.ms.dto.RoomDtos.RoomSummary;
import com.endstep.ms.dto.RoomDtos.RoomView;
import com.endstep.ms.entity.Deck;
import com.endstep.ms.entity.Room;
import com.endstep.ms.entity.RoomPlayer;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.DeckRepository;
import com.endstep.ms.repository.FormatRepository;
import com.endstep.ms.repository.RoomPlayerRepository;
import com.endstep.ms.repository.RoomRepository;
import com.endstep.ms.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Salas / lobby (endstep.txt secoes 9-10).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class RoomService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RoomRepository rooms;
    private final RoomPlayerRepository roomPlayers;
    private final DeckRepository decks;
    private final UserRepository users;
    private final FormatRepository formats;
    private final PasswordEncoder passwordEncoder;

    public RoomService(RoomRepository rooms, RoomPlayerRepository roomPlayers, DeckRepository decks,
                       UserRepository users, FormatRepository formats, PasswordEncoder passwordEncoder) {
        this.rooms = rooms;
        this.roomPlayers = roomPlayers;
        this.decks = decks;
        this.users = users;
        this.formats = formats;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RoomView create(Long userId, CreateRoomRequest req) {
        if (formats.findById(req.format()).isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Formato invalido");
        }
        int max = Math.max(2, Math.min(6, req.maxPlayers()));

        Room room = new Room();
        room.setRoomCode(uniqueCode());
        room.setHostUserId(userId);
        room.setName(req.name().trim());
        room.setFormat(req.format());
        room.setMaxPlayers(max);
        room.setVisibility(req.isPublic() ? Room.Visibility.PUBLIC.name() : Room.Visibility.PRIVATE.name());
        room.setAllowSpectators(req.allowSpectators());
        if (req.password() != null && !req.password().isBlank()) {
            room.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        rooms.save(room);

        RoomPlayer host = new RoomPlayer();
        host.setRoomId(room.getId());
        host.setUserId(userId);
        host.setRole(RoomPlayer.Role.PLAYER.name());
        roomPlayers.save(host);

        return view(room);
    }

    @Transactional(readOnly = true)
    public List<RoomSummary> listPublic() {
        return rooms.findByVisibilityAndStatusOrderByCreatedAtDesc(Room.Visibility.PUBLIC.name(), Room.Status.OPEN.name())
                .stream()
                .map(r -> {
                    long players = roomPlayers.findByRoomIdOrderByJoinedAt(r.getId()).stream()
                            .filter(p -> RoomPlayer.Role.PLAYER.name().equals(p.getRole())
                                    && !RoomPlayer.Status.LEFT.name().equals(p.getStatus()))
                            .count();
                    return new RoomSummary(r.getRoomCode(), r.getName(), r.getFormat(),
                            (int) players, r.getMaxPlayers(), r.getPasswordHash() != null, r.getStatus());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomView getByCode(String code) {
        return view(require(code));
    }

    @Transactional
    public RoomView join(Long userId, String code, JoinRoomRequest req) {
        Room room = require(code);
        if (Room.Status.CLOSED.name().equals(room.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Sala encerrada");
        }
        if (room.getPasswordHash() != null) {
            if (req == null || req.password() == null || !passwordEncoder.matches(req.password(), room.getPasswordHash())) {
                throw new ResponseStatusException(FORBIDDEN, "Senha incorreta");
            }
        }
        RoomPlayer existing = roomPlayers.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        boolean wantSpectator = req != null && req.asSpectator();

        if (existing != null) {
            existing.setStatus(RoomPlayer.Status.JOINED.name());
            roomPlayers.save(existing);
            return view(room);
        }
        List<RoomPlayer> current = roomPlayers.findByRoomIdOrderByJoinedAt(room.getId());
        long activePlayers = current.stream()
                .filter(p -> RoomPlayer.Role.PLAYER.name().equals(p.getRole())
                        && !RoomPlayer.Status.LEFT.name().equals(p.getStatus()))
                .count();

        RoomPlayer.Role role;
        if (wantSpectator) {
            if (!room.isAllowSpectators()) {
                throw new ResponseStatusException(FORBIDDEN, "Sala nao permite espectadores");
            }
            role = RoomPlayer.Role.SPECTATOR;
        } else if (activePlayers >= room.getMaxPlayers()) {
            if (!room.isAllowSpectators()) {
                throw new ResponseStatusException(CONFLICT,
                        "Sala cheia (" + room.getMaxPlayers() + " jogadores)");
            }
            throw new ResponseStatusException(CONFLICT,
                    "Sala cheia (" + room.getMaxPlayers() + " jogadores). Entre como espectador se quiser assistir.");
        } else {
            role = RoomPlayer.Role.PLAYER;
        }

        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(room.getId());
        rp.setUserId(userId);
        rp.setRole(role.name());
        roomPlayers.save(rp);
        return view(room);
    }

    @Transactional
    public void leave(Long userId, String code) {
        Room room = require(code);
        roomPlayers.findByRoomIdAndUserId(room.getId(), userId).ifPresent(rp -> {
            rp.setStatus(RoomPlayer.Status.LEFT.name());
            roomPlayers.save(rp);
        });
        if (room.getHostUserId().equals(userId) && !Room.Status.IN_GAME.name().equals(room.getStatus())) {
            room.setStatus(Room.Status.CLOSED.name());
            rooms.save(room);
        }
    }

    @Transactional
    public RoomView chooseDeck(Long userId, String code, ChooseDeckRequest req) {
        Room room = require(code);
        RoomPlayer rp = roomPlayers.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "Voce nao esta na sala"));
        if (req.deckId() == null) {
            rp.setDeckId(null);
            rp.setStatus(RoomPlayer.Status.JOINED.name());
            roomPlayers.save(rp);
            return view(room);
        }
        Deck deck = decks.findByIdAndUserId(req.deckId(), userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deck nao encontrado"));
        if (!deck.getFormat().equalsIgnoreCase(room.getFormat())) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "O deck e do formato " + deck.getFormat() + ", a sala e " + room.getFormat());
        }
        rp.setDeckId(deck.getId());
        rp.setStatus(RoomPlayer.Status.READY.name());
        roomPlayers.save(rp);
        return view(room);
    }

    Room require(String code) {
        return rooms.findByRoomCode(code)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Sala nao encontrada"));
    }

    RoomView view(Room room) {
        List<RoomPlayer> ps = roomPlayers.findByRoomIdOrderByJoinedAt(room.getId()).stream()
                .filter(p -> !RoomPlayer.Status.LEFT.name().equals(p.getStatus()))
                .toList();
        Map<Long, User> byId = users.findAllById(ps.stream().map(RoomPlayer::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Deck> deckById = decks.findAllById(
                        ps.stream().map(RoomPlayer::getDeckId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Deck::getId, Function.identity()));

        List<RoomPlayerView> players = new ArrayList<>();
        for (RoomPlayer p : ps) {
            User u = byId.get(p.getUserId());
            Deck d = p.getDeckId() == null ? null : deckById.get(p.getDeckId());
            players.add(new RoomPlayerView(
                    p.getUserId(),
                    u != null ? u.getUsername() : ("user#" + p.getUserId()),
                    p.getRole(),
                    p.getDeckId(),
                    d != null ? d.getName() : null,
                    p.getSeat(),
                    p.getStatus(),
                    room.getHostUserId().equals(p.getUserId())));
        }
        return new RoomView(
                room.getId(), room.getRoomCode(), room.getName(), room.getFormat(), room.getMaxPlayers(),
                room.getPasswordHash() != null, room.getVisibility(), room.isAllowSpectators(), room.getStatus(),
                room.getHostUserId(), room.getCurrentGameId(), players, room.getCreatedAt());
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!rooms.existsByRoomCode(code)) {
                return code;
            }
        }
        throw new ResponseStatusException(CONFLICT, "Nao foi possivel gerar codigo de sala");
    }
}
