package com.endstep.ms.repository;

import com.endstep.ms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositório de acesso a dados de User.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
