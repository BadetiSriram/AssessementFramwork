package com.aaseya.incident.repository;

import com.aaseya.incident.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Incident}. Per ArchUnit rule 1, only the
 * {@code application} and {@code domain} layers may depend on this interface (not web/workers).
 */
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
