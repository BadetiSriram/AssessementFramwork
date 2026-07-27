package com.aaseya.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customises the OpenAPI document served at {@code /v3/api-docs} and rendered by Swagger UI
 * at {@code /swagger-ui/index.html} (springdoc-openapi). Purely descriptive metadata.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service Sample API")
                .version("0.1.0")
                .description("""
                        Sample service built on camunda-process-framework (Camunda 8.9 SaaS).

                        - /orders …            create/read orders (starts the order-fulfillment process, job worker moves NEW → RESERVED)
                        - /orchestration/…     Camunda 8.9 Orchestration Cluster API v2 samples:
                            • Operate-style : search process instances, get/set variables
                            • Tasklist-style: search user tasks, assign, complete with parameters"""));
    }
}
