package com.endstep.ms.projection;

import java.util.UUID;

/**
 * oracle_id (Scryfall) -> id (PK interno). Usado no passe de rulings.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface OracleIdMapping {
    UUID getOracleId();

    Long getId();
}
