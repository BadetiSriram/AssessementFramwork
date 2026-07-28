package com.aaseya.incident.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI is at /swagger-ui/index.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI incidentResponseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Incident Response Service API")
                .version("0.1.0")
                .description("""
                        Cyber security incident response on Camunda 8.9.
                        POST /incidents raises an incident and starts the process; the human tasks
                        (containment verification, forensic analysis, CISO review, closure) can be
                        completed either here or in Camunda Tasklist."""));
    }
}
