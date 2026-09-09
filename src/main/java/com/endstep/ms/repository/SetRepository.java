package com.endstep.ms.repository;

import com.endstep.ms.entity.SetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositório de acesso a dados de Set.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface SetRepository extends JpaRepository<SetEntity, Long> {
    Optional<SetEntity> findByCode(String code);

    Optional<SetEntity> findByCodeIgnoreCase(String code);

    Page<SetEntity> findAllByOrderByReleasedAtDescCodeAsc(Pageable pageable);
}
