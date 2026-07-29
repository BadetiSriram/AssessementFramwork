package com.aaseya.incident.web;

import com.aaseya.camunda.framework.starter.web.Response;
import com.aaseya.incident.application.IncidentService;
import com.aaseya.incident.application.RaiseIncidentCommand;
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

/** Raise and read incidents. Raising one starts the incident-response process. */
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
        Incident incident = incidentService.raiseIncident(new RaiseIncidentCommand(
                request.title(), request.source(), request.forceIsolationFailure(),
                request.attackConfirmed(), request.assetCriticality(),
                request.dataExposed(), request.recordCount(), request.forceAiFailure(),
                request.slaDuration(), request.regulatoryDeadline(), request.responseActions()));
        return Response.ok(IncidentDto.from(incident));
    }

    @GetMapping("/{id}")
    public Response<IncidentDto> get(@PathVariable UUID id) {
        return Response.ok(IncidentDto.from(incidentService.getIncident(id)));
    }
}
