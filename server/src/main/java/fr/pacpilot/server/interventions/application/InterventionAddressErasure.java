package fr.pacpilot.server.interventions.application;

import fr.pacpilot.server.interventions.api.InterventionErasure;
import fr.pacpilot.server.interventions.application.port.out.InterventionRepository;
import fr.pacpilot.server.interventions.domain.Intervention;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satisfies {@link InterventionErasure}.
 *
 * <p>The address is replaced and the geocode removed; everything else about the visit survives. What
 * happened, when, how long it took and what it produced are the artisan's own operational record,
 * and none of it names anyone once the address is gone.
 */
@Service
class InterventionAddressErasure implements InterventionErasure {

    /** The same marker Dossier uses, so an erased record reads consistently across contexts. */
    private static final String REDACTED = "(efface)";

    private final InterventionRepository interventions;

    InterventionAddressErasure(InterventionRepository interventions) {
        this.interventions = interventions;
    }

    @Override
    @Transactional
    public int severAddressesForSite(UUID siteId, Instant at) {
        List<Intervention> visits = interventions.findBySiteId(siteId);

        visits.forEach(
                visit ->
                        interventions.save(
                                new Intervention(
                                        visit.id(),
                                        visit.siteId(),
                                        visit.type(),
                                        visit.status(),
                                        visit.scheduledAt(),
                                        visit.durationMinutes(),
                                        REDACTED,
                                        Optional.empty(),
                                        Optional.empty(),
                                        visit.outcomeNotes(),
                                        visit.dimensioningId(),
                                        visit.quoteId(),
                                        visit.cancellationReason(),
                                        visit.createdAt(),
                                        at)));
        return visits.size();
    }
}
