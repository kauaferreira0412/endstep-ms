package com.endstep.ms.config;

import com.endstep.ms.service.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * Autentica o frame STOMP CONNECT lendo o header "Authorization: Bearer &lt;jwt&gt;"
 * (o handshake HTTP do WebSocket nao carrega header pelo browser). O Principal
 * resultante tem o name = userId, usado depois para enviar em /user/{id}/queue/...
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {
    private final JwtService jwt;

    public StompAuthInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || !StompCommand.CONNECT.equals(acc.getCommand())) {
            return message;
        }
        String header = acc.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("STOMP CONNECT sem token");
        }
        JwtService.ParsedToken parsed = jwt.parse(header.substring(7));
        List<SimpleGrantedAuthority> authorities = parsed.roles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        Principal principal = new StompPrincipal(String.valueOf(parsed.userId()), parsed.username());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        acc.setUser(auth);
        return message;
    }

    public record StompPrincipal(String name, String username) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
