package com.aaseya.incident.repository;

import com.aaseya.incident.domain.IncidentTaskOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IncidentTaskOutcome}. Per ArchUnit rule 1, reachable only
 * from the {@code application}/{@code domain} layers.
 */
public interface IncidentTaskOutcomeRepository extends JpaRepository<IncidentTaskOutcome, UUID> {

    List<IncidentTaskOutcome> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
