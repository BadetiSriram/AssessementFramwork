package com.aaseya.incident.infrastructure.camunda;

import io.camunda.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;

/** Pushes the BPMN, DMN and forms to the cluster on startup. */
@Configuration
@Deployment(resources = {
        "classpath*:processes/*.bpmn",
        "classpath*:dmn/*.dmn",
        "classpath*:forms/*.form"
})
public class CamundaDeploymentConfig {
}
