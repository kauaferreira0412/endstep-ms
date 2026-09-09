package com.endstep.ms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada da aplicação Spring Boot do Endstep.
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EndstepMsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EndstepMsApplication.class, args);
    }
}
