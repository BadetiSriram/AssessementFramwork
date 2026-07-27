package com.aaseya.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the incident-response service (Assessment Use Case 4).
 *
 * <p>{@code @EnableScheduling} is required by the framework's {@code JdbcOutboxRelay}
 * scheduled poller (kept so the layout matches a production service).
 */
@SpringBootApplication
@EnableScheduling
public class IncidentResponseApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentResponseApplication.class, args);
    }
}
