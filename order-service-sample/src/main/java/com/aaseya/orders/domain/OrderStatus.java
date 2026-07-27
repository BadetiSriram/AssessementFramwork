package com.aaseya.orders.domain;

/**
 * Lifecycle states for an {@link Order}. The legal transitions between these states are
 * enforced by {@link Order#allowedTransitions(OrderStatus)}.
 *
 * <pre>
 *   NEW ──▶ RESERVED ──▶ FULFILLED
 *    │           │
 *    └───────────┴──▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {
    NEW,
    RESERVED,
    FULFILLED,
    CANCELLED
}
