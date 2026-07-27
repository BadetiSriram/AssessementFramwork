package com.aaseya.orders.domain;

import com.aaseya.camunda.framework.core.audit.AuditableEntity;
import com.aaseya.camunda.framework.starter.data.AuditColumnListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Order aggregate — the sample's domain heart.
 *
 * <p>Two framework hooks are demonstrated:
 * <ul>
 *   <li>Extends {@link AuditableEntity}&lt;{@link OrderStatus}&gt; so status changes go
 *       through a validated state machine ({@link #transition(Enum, String)} throws
 *       {@code IllegalStateTransitionException} on an illegal move).</li>
 *   <li>{@code @EntityListeners(AuditColumnListener.class)} — the framework stamps the
 *       four audit columns ({@code createdAt/updatedAt/createdBy/updatedBy}) reflectively
 *       by exact field name.</li>
 * </ul>
 *
 * <p>This class stays framework-pure per ArchUnit rule 3: it imports no Spring Web,
 * Servlet, or {@code io.camunda} types. SLF4J and JPA annotations are permitted.
 *
 * <p>The table is {@code orders} (not {@code order} — a SQL reserved word) and is created
 * by {@code V2__order_tables.sql}. Because {@code spring.jpa.hibernate.ddl-auto=validate},
 * every mapped column below must exist in that migration.
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditColumnListener.class)
public class Order extends AuditableEntity<OrderStatus> {

    private static final Logger log = LoggerFactory.getLogger(Order.class);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Correlation key carried through the BPMN process and used by the idempotency guard. */
    @Column(name = "business_key", nullable = false, updatable = false)
    private String businessKey;

    @Column(name = "product_sku", nullable = false)
    private String productSku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    // ---- Audit columns populated by AuditColumnListener (by exact field name) ----
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    /** JPA requires a no-arg constructor. */
    protected Order() {
    }

    /**
     * Factory for a brand-new order in state {@link OrderStatus#NEW}. The id doubles as the
     * business key so the same value flows REST → domain → BPMN → worker.
     *
     * @param productSku the ordered product's SKU
     * @param quantity   the ordered quantity
     * @return a new, unsaved {@code Order}
     */
    public static Order create(String productSku, int quantity) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.businessKey = order.id.toString();
        order.productSku = productSku;
        order.quantity = quantity;
        order.status = OrderStatus.NEW;
        return order;
    }

    // ---- AuditableEntity state-machine contract ----

    @Override
    protected Set<OrderStatus> allowedTransitions(OrderStatus from) {
        return switch (from) {
            case NEW       -> EnumSet.of(OrderStatus.RESERVED, OrderStatus.CANCELLED);
            case RESERVED  -> EnumSet.of(OrderStatus.FULFILLED, OrderStatus.CANCELLED);
            case FULFILLED -> EnumSet.noneOf(OrderStatus.class);
            case CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    @Override
    protected OrderStatus getStatus() {
        return status;
    }

    @Override
    protected void setStatus(OrderStatus status) {
        this.status = status;
    }

    @Override
    protected void appendAuditNote(String note, OrderStatus from, OrderStatus to) {
        // A production entity would persist an audit row here (e.g. a child collection).
        // For the sample we log within the same transaction as the status change.
        log.info("Order {} transition {} -> {}: {}", id, from, to, note);
    }

    // ---- Accessors (public getStatus so other layers can read state without the protected hook) ----

    public UUID getId() {
        return id;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public String getProductSku() {
        return productSku;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus status() {
        return status;
    }
}
