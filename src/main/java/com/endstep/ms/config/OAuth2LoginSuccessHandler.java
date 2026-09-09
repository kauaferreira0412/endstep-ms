package com.endstep.ms.config;

import com.endstep.ms.dto.AuthResponse;
import com.endstep.ms.entity.User;
import com.endstep.ms.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Apos o login com Google: provisiona o usuario, emite nossos tokens e
 * redireciona para o frontend com os tokens no fragmento (#) da URL.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final AuthService authService;
    private final EndstepProperties props;

    public OAuth2LoginSuccessHandler(AuthService authService, EndstepProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String sub = stringAttr(oauthUser, "sub");
        String email = stringAttr(oauthUser, "email");
        String name = stringAttr(oauthUser, "name");
        String picture = stringAttr(oauthUser, "picture");

        User user = authService.provisionGoogleUser(sub, email, name, picture);
        AuthResponse tokens = authService.issueTokens(user, request.getHeader("User-Agent"), request.getRemoteAddr());

        String target = props.auth().frontendUrl() + "/auth/callback#accessToken="
                + enc(tokens.accessToken()) + "&refreshToken=" + enc(tokens.refreshToken())
                + "&expiresIn=" + tokens.expiresInSeconds();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private static String stringAttr(OAuth2User user, String key) {
        Object v = user.getAttributes().get(key);
        return v == null ? null : v.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
