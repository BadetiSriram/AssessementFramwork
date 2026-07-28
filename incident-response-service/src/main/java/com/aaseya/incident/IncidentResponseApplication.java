package com.aaseya.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling is needed by the framework's JdbcOutboxRelay poller.
@SpringBootApplication
@EnableScheduling
public class IncidentResponseApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentResponseApplication.class, args);
    }
}
