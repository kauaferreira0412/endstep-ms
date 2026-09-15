package com.endstep.ms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Catálogo de conquistas: mesmos objetivos para todos os jogadores.
 *
 * @author Kauã Ferreira
 * @since 2026-09-15
 */
@Entity
@Table(name = "achievements")
@Getter
@Setter
public class Achievement {
    public enum Metric { GAMES_PLAYED, GAMES_WON, LEVEL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(nullable = false, length = 32)
    private String metric;

    @Column(nullable = false)
    private int target;

    @Column(name = "xp_reward", nullable = false)
    private int xpReward;

    @Column(nullable = false)
    private int position;
}
