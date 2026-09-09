package com.endstep.ms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registro do provedor Google montado programaticamente — ativa SOMENTE quando
 * a variavel de ambiente GOOGLE_CLIENT_ID esta preenchida. Assim nao depende de
 * profile do Spring: basta definir GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET.
 *
 * Sem essas variaveis: nenhum ClientRegistrationRepository e criado, o SecurityConfig
 * pula o oauth2Login e /api/auth/config devolve googleEnabled=false.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Configuration
@ConditionalOnExpression("'${GOOGLE_CLIENT_ID:}' != ''")
public class GoogleOAuthConfig {
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET}") String clientSecret) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }
}
