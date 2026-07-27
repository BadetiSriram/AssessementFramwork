package com.aaseya.orders.application;

import com.aaseya.camunda.framework.core.exception.BusinessException;
import com.aaseya.camunda.framework.core.process.ProcessService;
import com.aaseya.camunda.framework.core.process.StartProcessCommand;
import com.aaseya.orders.domain.Order;
import com.aaseya.orders.domain.OrderStatus;
import com.aaseya.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Use-case / orchestration layer. Coordinates the domain aggregate, persistence, and the
 * process engine.
 *
 * <p>Injects the framework's {@link ProcessService} <em>port</em> to talk to Camunda. That
 * is allowed outside {@code infrastructure.camunda} because {@code ProcessService} is
 * framework code, not an {@code io.camunda.client} type — ArchUnit rule 2 only forbids the
 * raw client. Transactions live here, never on the controller (rule 4).
 */
@Service
public class OrderService {

    /** BPMN process id — must match the {@code id} of the process in order-fulfillment.bpmn. */
    static final String PROCESS_ID = "order-fulfillment";

    private final OrderRepository orderRepository;
    private final ProcessService processService;

    public OrderService(OrderRepository orderRepository, ProcessService processService) {
        this.orderRepository = orderRepository;
        this.processService = processService;
    }

    /**
     * Creates an order and starts its fulfillment process.
     *
     * @param productSku ordered product SKU
     * @param quantity   ordered quantity
     * @return the persisted order
     */
    @Transactional
    public Order createOrder(String productSku, int quantity) {
        Order order = Order.create(productSku, quantity);
        orderRepository.save(order);

        // Start the BPMN process. The business key (= order id) is passed both as the
        // command's businessKey and as a process variable named exactly "businessKey", so
        // the worker's IdempotencyGuard picks it up automatically.
        processService.start(StartProcessCommand.withVariables(
                PROCESS_ID,
                order.getBusinessKey(),
                Map.of(
                        "businessKey", order.getBusinessKey(),
                        "orderId", order.getId().toString(),
                        "productSku", order.getProductSku(),
                        "quantity", order.getQuantity())));

        return order;
    }

    /**
     * Loads an order by id, or throws a {@link BusinessException} (→ HTTP 404/422 via the
     * framework's GlobalExceptionHandler) when it is unknown.
     *
     * @param id order id
     * @return the order
     */
    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND",
                        "No order with id " + id));
    }

    /**
     * Moves an order into {@link OrderStatus#RESERVED}. Invoked by the reserve-inventory
     * worker. Returns {@code false} when the order does not exist so the worker can raise a
     * BPMN business error rather than a technical failure.
     *
     * @param orderId order id as a string (the value carried in the process variables)
     * @return {@code true} if the order was found and reserved
     */
    @Transactional
    public boolean reserveInventory(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
                .map(order -> {
                    order.transition(OrderStatus.RESERVED, "Inventory reserved");
                    return true;
                })
                .orElse(false);
    }
}
