package com.aaseya.orders.web;

import com.aaseya.camunda.framework.starter.web.Response;
import com.aaseya.orders.application.OrderService;
import com.aaseya.orders.domain.Order;
import com.aaseya.orders.web.dto.CreateOrderRequest;
import com.aaseya.orders.web.dto.OrderDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST adapter for orders.
 *
 * <p>Conventions enforced by the framework's ArchUnit rules and demonstrated here:
 * <ul>
 *   <li>Every payload is wrapped in the framework {@link Response} envelope via
 *       {@code Response.ok(...)}.</li>
 *   <li>Returns and accepts DTOs/records only — never the {@code Order} entity (rule 5).</li>
 *   <li>No {@code @Transactional} on the controller (rule 4) — that lives in
 *       {@link OrderService}.</li>
 *   <li>Does not touch {@code repository}/{@code infrastructure} directly (rule 1) — it
 *       only calls the application service.</li>
 *   <li>Constructor injection, no {@code @Autowired} fields (rule 6).</li>
 * </ul>
 * Errors (e.g. unknown order) are thrown as {@code BusinessException} from the service and
 * mapped to RFC-7807 responses by the framework's GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<OrderDto> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.productSku(), request.quantity());
        return Response.ok(OrderDto.from(order));
    }

    @GetMapping("/{id}")
    public Response<OrderDto> get(@PathVariable UUID id) {
        Order order = orderService.getOrder(id);
        return Response.ok(OrderDto.from(order));
    }
}
