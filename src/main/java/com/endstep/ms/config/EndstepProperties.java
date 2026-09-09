package com.endstep.ms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracao da aplicacao (prefixo "endstep" no application.yml).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@ConfigurationProperties(prefix = "endstep")
public record EndstepProperties(
        Scryfall scryfall,
        CardSync cardSync,
        Admin admin,
        Cors cors,
        Auth auth,
        Storage storage,
        Translation translation
) {
    public record Translation(
            boolean enabled,
            String googleApiKey,
            String targetLang,
            String scryfallLang
    ) {
    }

    public record Storage(
            String type,
            String publicBaseUrl,
            Local local,
            R2 r2
    ) {
        public record Local(String dir) {
        }

        public record R2(
                String accountId,
                String accessKeyId,
                String secretAccessKey,
                String bucket,
                String publicBaseUrl
        ) {
        }
    }

    public record Scryfall(
            String apiBase,
            String bulkType,
            String userAgent,
            String downloadDir,
            long requestDelayMs
    ) {
    }

    public record CardSync(
            boolean runOnStartup
    ) {
    }

    public record Admin(
            String token
    ) {
    }

    public record Cors(
            List<String> allowedOrigins
    ) {
    }

    public record Auth(
            String jwtSecret,
            long accessTokenMinutes,
            long refreshTokenDays,
            List<String> adminEmails,
            String frontendUrl
    ) {
    }
}
