package com.endstep.ms.service;

/**
 * Principal exposto via @AuthenticationPrincipal nos controllers.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record AuthPrincipal(Long id, String username) {
}
