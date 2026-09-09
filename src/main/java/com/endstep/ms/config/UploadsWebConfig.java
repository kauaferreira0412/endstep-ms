package com.endstep.ms.config;

import com.endstep.ms.storage.LocalStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serve os arquivos do storage local em /uploads/**.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Configuration
@ConditionalOnProperty(prefix = "endstep.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class UploadsWebConfig implements WebMvcConfigurer {
    private final LocalStorageService storage;

    public UploadsWebConfig(LocalStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(storage.root().toUri().toString())
                .setCachePeriod(60 * 60 * 24 * 30);
    }
}
