package com.endstep.ms.repository;

import com.endstep.ms.entity.FormatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositório de acesso a dados de Format.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface FormatRepository extends JpaRepository<FormatEntity, String> {
    List<FormatEntity> findAllByOrderBySortOrderAsc();
}
