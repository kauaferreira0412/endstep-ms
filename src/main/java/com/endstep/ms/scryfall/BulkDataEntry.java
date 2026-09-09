package com.endstep.ms.scryfall;

/**
 * Metadados de um arquivo Bulk Data do Scryfall.
 * {@code downloadUri} aponta para o arquivo JSONL comprimido (.jsonl.gz)
 * e {@code size} e o tamanho comprimido em bytes.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record BulkDataEntry(
        String type,
        String downloadUri,
        long size,
        String updatedAt
) {
}
