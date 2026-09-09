package com.endstep.ms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuração da aplicação: AsyncConfig.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("cardSyncExecutor")
    public ThreadPoolTaskExecutor cardSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("card-sync-");
        executor.initialize();
        return executor;
    }
}
