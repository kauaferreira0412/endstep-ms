package com.endstep.ms.service;

import com.endstep.ms.config.EndstepProperties;
import com.endstep.ms.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Emissao e validacao do JWT de acesso (HMAC-SHA256).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class JwtService {
    private final SecretKey key;
    private final long accessTokenSeconds;

    public JwtService(EndstepProperties props) {
        String secret = props.auth().jwtSecret();
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenSeconds = Duration.ofMinutes(props.auth().accessTokenMinutes()).toSeconds();
    }

    public long accessTokenSeconds() {
        return accessTokenSeconds;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(r -> r.getName()).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenSeconds)))
                .signWith(key)
                .compact();
    }

    public ParsedToken parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) c.getOrDefault("roles", List.of());
        return new ParsedToken(
                Long.parseLong(c.getSubject()),
                c.get("username", String.class),
                roles);
    }

    public record ParsedToken(Long userId, String username, List<String> roles) {
    }
}
