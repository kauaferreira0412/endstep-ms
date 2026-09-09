package com.endstep.ms.controller;

import com.endstep.ms.dto.AuthResponse;
import com.endstep.ms.dto.LoginRequest;
import com.endstep.ms.dto.RefreshRequest;
import com.endstep.ms.dto.RegisterRequest;
import com.endstep.ms.service.AuthPrincipal;
import com.endstep.ms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints REST de Auth.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(AuthService auth,
                          org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> crr) {
        this.auth = auth;
        this.clientRegistrationRepository = crr.getIfAvailable();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return auth.register(req, http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) RefreshRequest req,
                       @AuthenticationPrincipal AuthPrincipal me) {
        if (req != null && req.refreshToken() != null) {
            auth.logout(req.refreshToken());
        } else if (me != null) {
            auth.logoutAll(me.id());
        }
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        boolean googleEnabled = clientRegistrationRepository != null
                && safeFindGoogle();
        return Map.of("googleEnabled", googleEnabled);
    }

    private boolean safeFindGoogle() {
        try {
            return clientRegistrationRepository.findByRegistrationId("google") != null;
        } catch (Exception e) {
            return false;
        }
    }
}
