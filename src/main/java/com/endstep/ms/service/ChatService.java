package com.endstep.ms.service;

import com.endstep.ms.dto.GameDtos.ChatView;
import com.endstep.ms.entity.ChatMessage;
import com.endstep.ms.entity.Game;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.ChatMessageRepository;
import com.endstep.ms.repository.GameRepository;
import com.endstep.ms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/**
 * Chat da partida (endstep.txt secao 27): sanitiza, limita spam, persiste, faz broadcast.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class ChatService {
    private static final int MAX_LEN = 500;
    private static final int WINDOW_MS = 5000;
    private static final int MAX_IN_WINDOW = 6;

    private final ChatMessageRepository chats;
    private final GameRepository games;
    private final UserRepository users;
    private final GameEventPublisher publisher;
    private final GameView gameView;

    private final ConcurrentHashMap<Long, Deque<Long>> recent = new ConcurrentHashMap<>();

    public ChatService(ChatMessageRepository chats, GameRepository games, UserRepository users,
                       GameEventPublisher publisher, GameView gameView) {
        this.chats = chats;
        this.games = games;
        this.users = users;
        this.publisher = publisher;
        this.gameView = gameView;
    }

    @Transactional
    public void postGameChat(long gameId, long userId, String rawText) {
        String body = sanitize(rawText);
        if (body.isEmpty()) {
            return;
        }
        if (rateLimited(userId)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Devagar com o chat");
        }
        Game game = games.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Partida nao encontrada"));

        ChatMessage m = new ChatMessage();
        m.setRoomId(game.getRoomId());
        m.setGameId(gameId);
        m.setUserId(userId);
        m.setBody(body);
        m.setKind(ChatMessage.Kind.USER.name());
        chats.save(m);

        String username = users.findById(userId).map(User::getUsername).orElse("user#" + userId);
        ChatView view = new ChatView(m.getId(), userId, username, body, m.getKind(), m.getCreatedAt());
        publisher.broadcastChat(gameId, gameView.participantUserIds(gameId), view);
    }

    private boolean rateLimited(long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> dq = recent.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW_MS) {
                dq.pollFirst();
            }
            if (dq.size() >= MAX_IN_WINDOW) {
                return true;
            }
            dq.addLast(now);
            return false;
        }
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("<", "").replace(">", "").trim();
        if (t.length() > MAX_LEN) {
            t = t.substring(0, MAX_LEN);
        }
        return t;
    }

    @Transactional
    public void system(long gameId, String text) {
        Game game = games.findById(gameId).orElse(null);
        if (game == null) {
            return;
        }
        ChatMessage m = new ChatMessage();
        m.setRoomId(game.getRoomId());
        m.setGameId(gameId);
        m.setBody(text);
        m.setKind(ChatMessage.Kind.SYSTEM.name());
        chats.save(m);
        ChatView view = new ChatView(m.getId(), null, "Sistema", text, m.getKind(), m.getCreatedAt());
        publisher.broadcastChat(gameId, gameView.participantUserIds(gameId), view);
    }
}
