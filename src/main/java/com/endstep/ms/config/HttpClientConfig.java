package com.endstep.ms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuração da aplicação: HttpClientConfig.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Configuration
public class HttpClientConfig {
    @Bean
    public HttpClient scryfallHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
