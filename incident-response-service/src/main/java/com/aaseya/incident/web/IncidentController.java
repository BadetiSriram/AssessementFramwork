package com.aaseya.incident.web;

import com.aaseya.camunda.framework.starter.web.Response;
import com.aaseya.incident.application.IncidentService;
import com.aaseya.incident.domain.Incident;
import com.aaseya.incident.web.dto.IncidentDto;
import com.aaseya.incident.web.dto.RaiseIncidentRequest;
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
 * REST adapter for incidents. Raising an incident starts the {@code incident-response} process;
 * all human tasks are then completed in Camunda Tasklist. DTO-only, non-transactional
 * (ArchUnit rules 4 & 5); constructor injection (rule 6).
 */
@RestController
@RequestMapping("/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response<IncidentDto> raise(@Valid @RequestBody RaiseIncidentRequest request) {
        boolean force = Boolean.TRUE.equals(request.forceIsolationFailure());
        Incident incident = incidentService.raiseIncident(request.title(), request.source(), force);
        return Response.ok(IncidentDto.from(incident));
    }

    @GetMapping("/{id}")
    public Response<IncidentDto> get(@PathVariable UUID id) {
        return Response.ok(IncidentDto.from(incidentService.getIncident(id)));
    }
}
