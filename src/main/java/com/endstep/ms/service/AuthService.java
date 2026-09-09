package com.endstep.ms.service;

import com.endstep.ms.config.EndstepProperties;
import com.endstep.ms.dto.AuthResponse;
import com.endstep.ms.dto.LoginRequest;
import com.endstep.ms.dto.RegisterRequest;
import com.endstep.ms.dto.UserView;
import com.endstep.ms.entity.RefreshToken;
import com.endstep.ms.entity.Role;
import com.endstep.ms.entity.User;
import com.endstep.ms.repository.RefreshTokenRepository;
import com.endstep.ms.repository.RoleRepository;
import com.endstep.ms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Serviço de Auth.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final EndstepProperties props;
    private final PermissionService permissions;

    public AuthService(UserRepository users,
                       RoleRepository roles,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder,
                       JwtService jwt,
                       EndstepProperties props,
                       PermissionService permissions) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.props = props;
        this.permissions = permissions;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req, String userAgent, String ip) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String username = req.username().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email ja cadastrado");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username ja em uso");
        }
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setDisplayName(req.displayName().trim());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setProvider(User.Provider.LOCAL.name());
        assignRoles(u, email);
        u = users.save(u);
        return issueTokens(u, userAgent, ip);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String userAgent, String ip) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        User u = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas"));
        if (u.getPasswordHash() == null || !passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
        }
        return issueTokens(u, userAgent, ip);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ip) {
        String hash = sha256Hex(rawRefreshToken);
        RefreshToken token = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalido"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expirado");
        }
        token.setRevoked(true);
        refreshTokens.save(token);
        User u = users.findById(token.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
        return issueTokens(u, userAgent, ip);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(sha256Hex(rawRefreshToken)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokens.save(t);
        });
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    @Transactional
    public User provisionGoogleUser(String googleSub, String email, String name, String pictureUrl) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

        return users.findByProviderAndProviderId(User.Provider.GOOGLE.name(), googleSub)
                .or(() -> normalizedEmail == null ? Optional.<User>empty()
                        : users.findByEmailIgnoreCase(normalizedEmail))
                .map(existing -> {
                    if (!User.Provider.GOOGLE.name().equals(existing.getProvider())) {
                        existing.setProvider(User.Provider.GOOGLE.name());
                    }
                    existing.setProviderId(googleSub);
                    if (existing.getAvatarUrl() == null && pictureUrl != null) {
                        existing.setAvatarUrl(pictureUrl);
                    }
                    existing.setEmailVerified(true);
                    return users.save(existing);
                })
                .orElseGet(() -> {
                    User u = new User();
                    u.setEmail(normalizedEmail != null ? normalizedEmail : googleSub + "@google.local");
                    u.setUsername(uniqueUsernameFrom(normalizedEmail, name, googleSub));
                    u.setDisplayName(name != null && !name.isBlank() ? name : "Jogador");
                    u.setProvider(User.Provider.GOOGLE.name());
                    u.setProviderId(googleSub);
                    u.setAvatarUrl(pictureUrl);
                    u.setEmailVerified(true);
                    assignRoles(u, u.getEmail());
                    return users.save(u);
                });
    }

    public AuthResponse issueTokens(User u, String userAgent, String ip) {
        String access = jwt.issueAccessToken(u);

        String rawRefresh = randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(sha256Hex(rawRefresh));
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(props.auth().refreshTokenDays())));
        rt.setUserAgent(truncate(userAgent, 255));
        rt.setIp(truncate(ip, 64));
        refreshTokens.save(rt);

        return AuthResponse.of(access, rawRefresh, jwt.accessTokenSeconds(),
                UserView.of(u, permissions.codesOf(u.getId())));
    }

    private void assignRoles(User u, String email) {
        u.getRoles().add(roleOrThrow(Role.USER));
        List<String> admins = props.auth().adminEmails();
        if (admins != null && email != null && admins.stream().anyMatch(a -> a.equalsIgnoreCase(email))) {
            u.getRoles().add(roleOrThrow(Role.ADMIN));
        }
    }

    private Role roleOrThrow(String name) {
        return roles.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Role ausente no banco: " + name));
    }

    private String uniqueUsernameFrom(String email, String name, String fallback) {
        String base = email != null ? email.substring(0, email.indexOf('@'))
                : (name != null ? name : fallback);
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
        if (base.length() < 3) {
            base = "player" + base;
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        String candidate = base;
        int i = 1;
        while (users.existsByUsernameIgnoreCase(candidate)) {
            candidate = base + i++;
        }
        return candidate;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
