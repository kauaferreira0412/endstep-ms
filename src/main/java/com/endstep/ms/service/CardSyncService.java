package com.endstep.ms.service;
import com.endstep.ms.scryfall.ScryfallClient;
import com.endstep.ms.scryfall.BulkDataEntry;
import com.endstep.ms.repository.CardSyncRunRepository;
import com.endstep.ms.entity.CardSyncRun;
import com.endstep.ms.common.SyncCounters;

import com.endstep.ms.config.EndstepProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * CardSyncJob (endstep.txt secao 5). Baixa o Bulk Data do Scryfall,
 * faz streaming do JSON e grava as cartas via {@link CardIngestService} (JPA).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@Service
public class CardSyncService {
    private static final Logger log = LoggerFactory.getLogger(CardSyncService.class);
    private static final int PROGRESS_EVERY = 2500;
    private static final int CARD_CHUNK = 500;
    private static final int RULINGS_BATCH = 2000;

    private final ScryfallClient scryfall;
    private final ObjectMapper om;
    private final EndstepProperties props;
    private final CardIngestService ingest;
    private final CardSyncRunRepository runs;

    public CardSyncService(ScryfallClient scryfall,
                           ObjectMapper om,
                           EndstepProperties props,
                           CardIngestService ingest,
                           CardSyncRunRepository runs) {
        this.scryfall = scryfall;
        this.om = om;
        this.props = props;
        this.ingest = ingest;
        this.runs = runs;
    }

    @Transactional
    public int failOrphanedRuns() {
        List<CardSyncRun> stuck = runs.findByStatus(CardSyncRun.RUNNING);
        for (CardSyncRun r : stuck) {
            r.setStatus(CardSyncRun.FAILED);
            r.setFinishedAt(Instant.now());
            r.setMessage("Interrompida: aplicacao reiniciada");
        }
        runs.saveAll(stuck);
        return stuck.size();
    }

    public long startRun() {
        if (runs.existsByStatus(CardSyncRun.RUNNING)) {
            throw new IllegalStateException("Ja existe uma sincronizacao em andamento");
        }
        CardSyncRun run = new CardSyncRun();
        run.setSource("scryfall");
        run.setBulkType(props.scryfall().bulkType());
        run.setStatus(CardSyncRun.RUNNING);
        long id = runs.save(run).getId();
        log.info("Sync {} criada (bulk={})", id, props.scryfall().bulkType());
        return id;
    }

    @Async("cardSyncExecutor")
    public void runAsync(long runId) {
        try {
            doSync(runId);
        } catch (Exception e) {
            log.error("Sync {} falhou", runId, e);
            markFailed(runId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    void doSync(long runId) throws Exception {
        long t0 = System.currentTimeMillis();
        SyncCounters c = new SyncCounters();

        BulkDataEntry entry = scryfall.getBulkEntry(props.scryfall().bulkType());
        Path file = ensureLocalCopy(entry);
        setDownloadBytes(runId, Files.size(file));

        Set<UUID> oraclesDone = new HashSet<>(120_000);
        List<JsonNode> chunk = new ArrayList<>(CARD_CHUNK);
        int lastProgress = 0;
        log.info("Sync {}: processando cartas (lotes de {})...", runId, CARD_CHUNK);
        try (InputStream in = openJsonlGz(file);
             MappingIterator<JsonNode> it = om.readerFor(JsonNode.class).readValues(in)) {
            while (it.hasNext()) {
                chunk.add(it.next());
                if (chunk.size() >= CARD_CHUNK) {
                    flushChunk(chunk, oraclesDone, c);
                    chunk = new ArrayList<>(CARD_CHUNK);
                    log.info("Sync {}: {} cartas ({} novas, {} atualizadas, {} erros)",
                            runId, c.processed, c.printingsInserted, c.printingsUpdated, c.errors);
                    if (c.processed - lastProgress >= PROGRESS_EVERY) {
                        saveProgress(runId, c);
                        lastProgress = c.processed;
                    }
                }
            }
            if (!chunk.isEmpty()) {
                flushChunk(chunk, oraclesDone, c);
            }
        }
        saveProgress(runId, c);
        log.info("Sync {}: cartas concluidas ({}). Importando rulings...", runId, c);

        try {
            importRulings(c);
        } catch (Exception e) {
            log.warn("Sync {}: falha ao importar rulings ({})", runId, e.toString());
        }

        finishRun(runId, c);
        log.info("Sync {} concluida em {} ms: {}", runId, System.currentTimeMillis() - t0, c);
    }

    private void flushChunk(List<JsonNode> chunk, Set<UUID> oraclesDone, SyncCounters c) {
        try {
            CardIngestService.BatchResult r = ingest.ingestBatch(chunk, oraclesDone);
            c.printingsInserted += r.inserted();
            c.printingsUpdated += r.updated();
            c.facesWritten += r.faces();
            c.skipped += r.skipped();
            oraclesDone.addAll(r.oraclesTouched());
            c.oraclesTouched += r.oraclesTouched().size();
        } catch (Exception batchEx) {
            log.warn("Lote de {} cartas falhou ({}); reprocessando individualmente",
                    chunk.size(), batchEx.toString());
            for (JsonNode card : chunk) {
                try {
                    ingest.ingestCard(card, oraclesDone, c);
                } catch (SkippedCardException e) {
                    c.skipped++;
                } catch (Exception e) {
                    c.errors++;
                    if (c.errors <= 20) {
                        log.warn("Falha ao gravar carta '{}': {}", card.path("name").asText("?"), e.toString());
                    }
                }
            }
        }
        c.processed += chunk.size();
    }

    private void importRulings(SyncCounters c) throws Exception {
        BulkDataEntry entry = scryfall.getBulkEntry("rulings");
        Path file = ensureLocalCopy(entry);

        ingest.clearRulings();
        Map<UUID, Long> oracleIndex = ingest.loadOracleIdIndex();

        List<JsonNode> batch = new ArrayList<>(RULINGS_BATCH);
        try (InputStream in = openJsonlGz(file);
             MappingIterator<JsonNode> it = om.readerFor(JsonNode.class).readValues(in)) {
            while (it.hasNext()) {
                batch.add(it.next());
                if (batch.size() >= RULINGS_BATCH) {
                    c.rulingsWritten += ingest.saveRulingsBatch(List.copyOf(batch), oracleIndex);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                c.rulingsWritten += ingest.saveRulingsBatch(List.copyOf(batch), oracleIndex);
            }
        }
        log.info("Rulings importadas: {}", c.rulingsWritten);
    }

    private void setDownloadBytes(long runId, long bytes) {
        runs.findById(runId).ifPresent(r -> {
            r.setDownloadBytes(bytes);
            runs.save(r);
        });
    }

    private void saveProgress(long runId, SyncCounters c) {
        runs.findById(runId).ifPresent(r -> {
            r.applyCounters(c);
            runs.save(r);
        });
    }

    private void finishRun(long runId, SyncCounters c) {
        runs.findById(runId).ifPresent(r -> {
            r.applyCounters(c);
            r.setStatus(CardSyncRun.COMPLETED);
            r.setFinishedAt(Instant.now());
            r.setMessage(truncate(c.toString()));
            runs.save(r);
        });
    }

    private void markFailed(long runId, String message) {
        runs.findById(runId).ifPresent(r -> {
            r.setStatus(CardSyncRun.FAILED);
            r.setFinishedAt(Instant.now());
            r.setMessage(truncate(message));
            runs.save(r);
        });
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private Path ensureLocalCopy(BulkDataEntry entry) throws Exception {
        Path dir = Path.of(props.scryfall().downloadDir());
        Files.createDirectories(dir);
        String stamp = entry.updatedAt().replaceAll("[^0-9]", "");
        if (stamp.length() > 8) {
            stamp = stamp.substring(0, 8);
        }
        Path file = dir.resolve(entry.type() + "-" + stamp + ".jsonl.gz");

        if (Files.exists(file) && Files.size(file) > 0) {
            log.info("Reutilizando arquivo local {}", file);
            return file;
        }

        log.info("Baixando {} (~{} MB comprimidos) de {}",
                entry.type(), entry.size() / (1024 * 1024), entry.downloadUri());
        Path tmp = Files.createTempFile(dir, entry.type() + "-", ".part");
        try (InputStream in = scryfall.openDownloadStream(entry.downloadUri())) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        log.info("Download concluido: {} ({} bytes)", file, Files.size(file));
        return file;
    }

    private static InputStream openJsonlGz(Path file) throws Exception {
        return new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16), 1 << 16);
    }
}
