package com.endstep.ms.controller;

import com.endstep.ms.dto.SyncLogView;
import com.endstep.ms.entity.CardSyncRun;
import com.endstep.ms.repository.CardSyncRunRepository;
import com.endstep.ms.service.CardSyncService;
import com.endstep.ms.service.SyncLogBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Rotas protegidas: exigem papel ADMIN (ver SecurityConfig + @PreAuthorize).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/api/admin/cards")
@PreAuthorize("hasRole('ADMIN') or @perm.has('SYNC')")
public class AdminSyncController {
    private final CardSyncService syncService;
    private final CardSyncRunRepository runs;
    private final SyncLogBuffer syncLog;

    public AdminSyncController(CardSyncService syncService, CardSyncRunRepository runs,
                              SyncLogBuffer syncLog) {
        this.syncService = syncService;
        this.runs = runs;
        this.syncLog = syncLog;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> trigger() {
        long runId = syncService.startRun();
        syncService.runAsync(runId);
        return ResponseEntity.accepted().body(Map.of("runId", runId, "status", "RUNNING"));
    }

    @GetMapping("/sync/status")
    public List<CardSyncRun> status() {
        return runs.findTop10ByOrderByIdDesc();
    }

    @GetMapping("/sync/log")
    public SyncLogView log(@RequestParam(defaultValue = "0") long since) {
        List<SyncLogView.Line> lines = syncLog.since(since).stream()
                .map(l -> new SyncLogView.Line(l.seq(), l.at().toString(), l.level(), l.message()))
                .toList();
        return new SyncLogView(syncLog.lastSeq(), lines);
    }
}
