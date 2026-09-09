package com.endstep.ms.projection;

/**
 * Uma carta no ranking da home (comandante ou carta mais jogada).
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public interface StatEntryRow {
    String getOracleId();
    String getName();
    long getGames();
    String getImage();
}
