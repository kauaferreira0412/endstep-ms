package com.endstep.ms.controller;

import com.endstep.ms.service.ChatService;
import com.endstep.ms.service.GameEngine;
import com.endstep.ms.service.GameEventPublisher;
import com.endstep.ms.service.GamePresenceService;
import com.endstep.ms.service.GameView;
import com.endstep.ms.service.game.EngineResult;
import com.endstep.ms.service.game.GameActionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * Entrada STOMP da mesa: /app/game/{id}/join | /act | /chat (endstep.txt secao 42).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Controller
public class GameSocketController {
    private static final Logger log = LoggerFactory.getLogger(GameSocketController.class);

    private final GameEngine engine;
    private final GameEventPublisher publisher;
    private final GamePresenceService presence;
    private final GameView gameView;
    private final ChatService chat;
    private final SimpMessagingTemplate messaging;

    public GameSocketController(GameEngine engine, GameEventPublisher publisher, GamePresenceService presence,
                               GameView gameView, ChatService chat, SimpMessagingTemplate messaging) {
        this.engine = engine;
        this.publisher = publisher;
        this.presence = presence;
        this.gameView = gameView;
        this.chat = chat;
        this.messaging = messaging;
    }

    @MessageMapping("/game/{gameId}/join")
    public void join(@DestinationVariable long gameId, Principal principal,
                     @Header("simpSessionId") String sessionId) {
        long userId = uid(principal);
        requireParticipant(gameId, userId);
        presence.onJoin(sessionId, gameId, userId);
        publisher.sendSnapshot(gameId, userId);
    }

    @MessageMapping("/game/{gameId}/act")
    public void act(@DestinationVariable long gameId, Principal principal,
                    @Payload GameActionMessage msg) {
        long userId = uid(principal);
        requireParticipant(gameId, userId);
        try {
            EngineResult result = engine.apply(gameId, userId, msg);
            publisher.broadcastPatch(gameId, result);
            if (result.actorNotice() != null) {
                messaging.convertAndSendToUser(String.valueOf(userId), "/queue/game/" + gameId,
                        Map.of("type", "NOTICE", "data", result.actorNotice()));
            }
        } catch (ResponseStatusException e) {
            sendError(userId, gameId, e.getReason());
        } catch (Exception e) {
            log.warn("Falha na acao {} do jogo {}: {}", msg.type(), gameId, e.toString());
            sendError(userId, gameId, "Nao foi possivel executar a acao");
        }
    }

    @MessageMapping("/game/{gameId}/chat")
    public void chat(@DestinationVariable long gameId, Principal principal, @Payload Map<String, Object> body) {
        long userId = uid(principal);
        requireParticipant(gameId, userId);
        Object text = body.get("text");
        try {
            chat.postGameChat(gameId, userId, text == null ? "" : String.valueOf(text));
        } catch (ResponseStatusException e) {
            sendError(userId, gameId, e.getReason());
        }
    }

    private void requireParticipant(long gameId, long userId) {
        if (!gameView.participantUserIds(gameId).contains(userId)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Voce nao participa desta partida");
        }
    }

    private void sendError(long userId, long gameId, String message) {
        messaging.convertAndSendToUser(String.valueOf(userId), "/queue/game/" + gameId,
                Map.of("type", "ERROR", "data", Map.of("message", message == null ? "Erro" : message)));
    }

    private static long uid(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Sem sessao");
        }
        return Long.parseLong(principal.getName());
    }
}
