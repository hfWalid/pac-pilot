package fr.pacpilot.server.interventions.application.port.out;

import fr.pacpilot.server.interventions.domain.Intervention;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port — where the timeline is kept. Upsert, as everywhere else here. */
public interface InterventionRepository {

    Intervention save(Intervention intervention);

    Optional<Intervention> findById(UUID id);

    /** Most recent first — the order the timeline reads. */
    List<Intervention> findBySiteId(UUID siteId);
}
