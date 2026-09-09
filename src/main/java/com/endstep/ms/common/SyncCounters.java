package com.endstep.ms.common;

/**
 * Contadores mutaveis acumulados durante uma sincronizacao.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public class SyncCounters {
    public int processed;
    public int printingsInserted;
    public int printingsUpdated;
    public int oraclesTouched;
    public int facesWritten;
    public int rulingsWritten;
    public int skipped;
    public int errors;

    @Override
    public String toString() {
        return "processed=" + processed
                + " printingsInserted=" + printingsInserted
                + " printingsUpdated=" + printingsUpdated
                + " oraclesTouched=" + oraclesTouched
                + " faces=" + facesWritten
                + " rulings=" + rulingsWritten
                + " skipped=" + skipped
                + " errors=" + errors;
    }
}
