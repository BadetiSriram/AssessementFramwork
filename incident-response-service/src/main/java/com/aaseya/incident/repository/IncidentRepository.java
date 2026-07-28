package com.aaseya.incident.repository;

import com.aaseya.incident.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
}
