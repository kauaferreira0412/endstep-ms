package com.endstep.ms.common;

import java.util.List;

/**
 * Tipo utilitário/base da aplicação: PageResponse.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
