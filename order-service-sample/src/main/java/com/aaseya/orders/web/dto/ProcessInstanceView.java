package com.aaseya.orders.web.dto;

/**
 * Read model for a process instance, as returned by the Camunda 8.9 Orchestration
 * Cluster API v2 search (the "Operate" replacement). DTO record — safe to expose from a
 * controller (ArchUnit rule #5 forbids returning JPA entities, not records).
 */
public record ProcessInstanceView(
        Long processInstanceKey,
        String processDefinitionId,
        String state,
        String startDate) {
}
