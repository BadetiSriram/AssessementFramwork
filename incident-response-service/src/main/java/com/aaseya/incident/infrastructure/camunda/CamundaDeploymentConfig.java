package com.aaseya.incident.infrastructure.camunda;

import io.camunda.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;

/**
 * Deploys all BPMN and DMN resources to the connected Camunda 8.9 cluster on startup.
 *
 * <p>Lives under {@code infrastructure.camunda} because it imports {@code io.camunda.client}
 * ({@link Deployment}), which ArchUnit rule #2 permits only in that package. The Camunda SDK's
 * {@code DeploymentAnnotationProcessor} uploads the referenced resources once the client is ready.
 */
@Configuration
@Deployment(resources = {
        "classpath*:processes/*.bpmn",
        "classpath*:dmn/*.dmn"
})
public class CamundaDeploymentConfig {
}
