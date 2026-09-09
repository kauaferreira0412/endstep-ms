package com.endstep.ms.storage;

/**
 * Guarda arquivos publicos (imagens de cartas customizadas). Impl local ou Cloudflare R2.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface StorageService {
    Stored put(String folder, String filename, byte[] data, String contentType);

    void delete(String url);

    record Stored(String url, String key) {
    }
}
