package com.aaseya.orders.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Inbound request body for {@code POST /orders}. Bean-validation failures are turned into
 * an RFC-7807 {@code 400} response with a {@code fieldErrors} array by the framework's
 * GlobalExceptionHandler.
 *
 * @param productSku ordered product SKU; required
 * @param quantity   ordered quantity; at least 1
 */
public record CreateOrderRequest(
        @NotBlank String productSku,
        @Min(1) int quantity) {
}
