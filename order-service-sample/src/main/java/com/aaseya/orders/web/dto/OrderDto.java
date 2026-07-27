package com.aaseya.orders.web.dto;

import com.aaseya.orders.domain.Order;

/**
 * Outbound representation of an {@link Order}. This DTO — never the JPA {@code @Entity} —
 * crosses the HTTP boundary (ArchUnit rule 5).
 *
 * @param id         order id
 * @param productSku ordered product SKU
 * @param quantity   ordered quantity
 * @param status     current lifecycle state
 */
public record OrderDto(String id, String productSku, int quantity, String status) {

    /**
     * Maps a domain {@link Order} to its API representation.
     *
     * @param order the domain aggregate
     * @return a DTO snapshot
     */
    public static OrderDto from(Order order) {
        return new OrderDto(
                order.getId().toString(),
                order.getProductSku(),
                order.getQuantity(),
                order.status().name());
    }
}
