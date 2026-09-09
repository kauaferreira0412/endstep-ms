package com.endstep.ms.dto;

import com.endstep.ms.entity.FormatEntity;

/**
 * Objetos de transferência de dados (FormatView).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record FormatView(
        String code,
        String name,
        Integer minDeck,
        Integer maxDeck,
        int maxCopies,
        boolean singleton,
        boolean usesCommandZone,
        int defaultLife,
        boolean enforceColorIdentity,
        int sideboardMax
) {
    public static FormatView of(FormatEntity f) {
        return new FormatView(f.getCode(), f.getName(), f.getMinDeck(), f.getMaxDeck(), f.getMaxCopies(),
                f.isSingleton(), f.isUsesCommandZone(), f.getDefaultLife(), f.isEnforceColorIdentity(),
                f.getSideboardMax());
    }
}
