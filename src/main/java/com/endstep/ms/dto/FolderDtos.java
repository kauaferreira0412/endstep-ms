package com.endstep.ms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Objetos de transferência de dados (FolderDtos).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class FolderDtos {
    private FolderDtos() {
    }

    public record FolderView(
            Long id,
            Long parentId,
            String name,
            int position,
            long deckCount,
            Instant createdAt
    ) {
    }

    public record CreateFolderRequest(
            @NotBlank @Size(max = 120) String name,
            Long parentId
    ) {
    }

    public record UpdateFolderRequest(
            @Size(max = 120) String name,
            Long parentId,
            Integer position
    ) {
    }
}
