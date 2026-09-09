package com.endstep.ms.service;

import java.util.Arrays;
import java.util.List;

/**
 * Catalogo das telas/recursos que o admin pode liberar por usuario.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
public enum AppPermission {

    SYNC("Sincronização de cartas");

    private final String label;

    AppPermission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(p -> p.name().equals(code));
    }

    public static List<AppPermission> all() {
        return Arrays.asList(values());
    }
}
