package com.aaseya.orders.web.dto;

import java.util.Map;

/**
 * Inbound body to start a process instance by its BPMN process id, e.g.
 * {@code {"processDefinitionId": "order-approval", "variables": {"orderId": "abc"}}}.
 */
public record StartProcessRequest(String processDefinitionId, Map<String, Object> variables) {
}
