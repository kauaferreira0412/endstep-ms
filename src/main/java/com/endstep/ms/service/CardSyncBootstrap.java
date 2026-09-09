package com.endstep.ms.service;

import com.endstep.ms.config.EndstepProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Dispara a sincronizacao no startup quando endstep.card-sync.run-on-startup=true.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Component
public class CardSyncBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CardSyncBootstrap.class);

    private final EndstepProperties props;
    private final CardSyncService syncService;

    public CardSyncBootstrap(EndstepProperties props, CardSyncService syncService) {
        this.props = props;
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int freed = syncService.failOrphanedRuns();
        if (freed > 0) {
            log.warn("{} sincronizacao(oes) presa(s) em RUNNING marcada(s) como FAILED (reinicio)", freed);
        }
        if (!props.cardSync().runOnStartup()) {
            return;
        }
        try {
            long runId = syncService.startRun();
            syncService.runAsync(runId);
            log.info("Sync automatica no startup disparada (run {})", runId);
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel disparar sync no startup: {}", e.getMessage());
        }
    }
}
