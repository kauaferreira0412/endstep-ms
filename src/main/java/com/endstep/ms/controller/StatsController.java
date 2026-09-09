package com.endstep.ms.controller;

import com.endstep.ms.dto.HomeStats;
import com.endstep.ms.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dados da home (numeros da plataforma + rankings automaticos).
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/home")
    public HomeStats home() {
        return stats.home();
    }
}
