package com.endstep.ms.dto;

import java.util.List;

/**
 * Trecho do log da sincronizacao para a tela de admin: as linhas novas desde
 * {@code since} e o numero da ultima linha conhecida.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public record SyncLogView(long lastSeq, List<Line> lines) {

    public record Line(long seq, String at, String level, String message) {
    }
}
