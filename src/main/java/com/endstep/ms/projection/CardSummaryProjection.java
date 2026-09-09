package com.endstep.ms.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projecao de resultado de busca (native query).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface CardSummaryProjection {
    UUID getOracleId();

    String getName();

    String getManaCost();

    BigDecimal getManaValue();

    String getTypeLine();

    String getColorIdentity();

    int getPrintingCount();

    Long getRepresentativePrintingId();

    String getSetCode();

    String getImageSmall();

    String getImageNormal();
}
