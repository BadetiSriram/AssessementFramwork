package com.aaseya.orders.repository;

import com.aaseya.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Order}.
 *
 * <p>Per ArchUnit rule 1, classes in {@code ..repository..} must not be reached directly
 * from {@code ..web..} or {@code ..workers..}. Only the {@code application} and
 * {@code domain} layers depend on this interface.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {
}
