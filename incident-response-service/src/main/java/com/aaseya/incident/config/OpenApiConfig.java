package com.aaseya.incident.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document served at {@code /v3/api-docs}, rendered by Swagger UI at
 * {@code /swagger-ui/index.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI incidentResponseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Incident Response Service API")
                .version("0.1.0")
                .description("""
                        Use Case 4 — Cyber Security Incident Response on Camunda 8.9.
                        - POST /incidents        raise an incident (starts the incident-response process)
                        - GET  /incidents/{id}    read incident state
                        Human tasks (containment verification, forensic analysis, CISO review, closure)
                        are completed in Camunda Tasklist."""));
    }
}
