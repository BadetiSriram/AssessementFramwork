package com.aaseya.orders.infrastructure.camunda;

import io.camunda.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;

/**
 * Deploys the {@code order-fulfillment} BPMN process to the connected Camunda 8.9 cluster on
 * application startup, so that {@code POST /orders} can create process instances.
 *
 * <p>The Camunda Spring SDK's {@code DeploymentAnnotationProcessor} picks up this
 * {@link Deployment} annotation and uploads the referenced resource once the client is ready.
 *
 * <p>This class lives under {@code infrastructure.camunda} because it imports
 * {@code io.camunda.client..}, which ArchUnit rule #2 permits only in that package.
 */
@Configuration
@Deployment(resources = "classpath*:processes/*.bpmn")
public class CamundaDeploymentConfig {
}
