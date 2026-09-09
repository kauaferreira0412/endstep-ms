package com.endstep.ms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link PasswordEncoder} fica aqui (nao no SecurityConfig) para evitar
 * ciclo: SecurityConfig -> OAuth2LoginSuccessHandler -> AuthService -> PasswordEncoder.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Configuration
public class CryptoConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
