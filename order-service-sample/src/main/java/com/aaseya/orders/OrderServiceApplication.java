package com.aaseya.orders;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the order-service sample.
 *
 * <p>{@code @EnableScheduling} is required by the framework's {@code JdbcOutboxRelay}
 * scheduled poller. This sample does not write to the outbox in its Standard shape, but
 * the annotation is kept so the layout matches a production service (and so adding the
 * outbox pattern later is a no-op wiring change).
 */
@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
