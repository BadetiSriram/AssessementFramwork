package com.aaseya.camunda.service.template;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scaffolding entry point. Copy this module as the starting point for a new service;
 * rename the base package and add your workers, BPMN, and domain code.
 *
 * <p>{@code @EnableScheduling} is required for {@code JdbcOutboxRelay}'s
 * {@code @Scheduled} poller to fire automatically.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    /**
     * Launches the Spring Boot application.
     *
     * @param args command-line arguments passed through to {@link SpringApplication}
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
