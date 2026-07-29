package com.aaseya.incident.application;

import com.aaseya.incident.domain.IncidentTaskOutcome;
import com.aaseya.incident.repository.IncidentTaskOutcomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records the automated things that happen to an incident - timer escalations and AI fallbacks -
 * in the same table as the human task outcomes.
 *
 * <p>Without this the only evidence a boundary event fired is a log line, which is no use to a
 * demo or a regulator. They land in {@code GET /incidents/{id}/tasks/outcomes} alongside the human
 * completions, in order, which is exactly the audit trail you want to show.
 */
@Service
public class IncidentEventService {

    /** userTaskKey for rows that aren't a user task at all. */
    static final long SYSTEM_EVENT = 0L;

    private static final String SYSTEM_ACTOR = "system:process";

    private final IncidentTaskOutcomeRepository outcomeRepository;

    public IncidentEventService(IncidentTaskOutcomeRepository outcomeRepository) {
        this.outcomeRepository = outcomeRepository;
    }

    /** detailJson is stored verbatim, so pass a JSON object literal. */
    @Transactional
    public void record(String incidentId, String elementId, String eventName, String detailJson) {
        outcomeRepository.save(IncidentTaskOutcome.of(
                UUID.fromString(incidentId), SYSTEM_EVENT, elementId, eventName,
                SYSTEM_ACTOR, detailJson));
    }
}
