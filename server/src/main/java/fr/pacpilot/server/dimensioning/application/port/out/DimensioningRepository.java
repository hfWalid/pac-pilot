package fr.pacpilot.server.dimensioning.application.port.out;

import fr.pacpilot.core.dimensioning.model.Dimensioning;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port — where heat-loss studies are kept.
 *
 * <p>Takes and returns the {@code :core} aggregate rather than a server-side copy. Unlike
 * {@code dossier}, whose records are server-owned and share nothing with the core, {@code
 * Dimensioning} <b>is</b> a core type (DELIVERY-PLAN §3): the PWA computes it offline and the server
 * recomputes it. A parallel server-side shape here would be a second definition of the thing the
 * whole client/server correctness contract is written against.
 */
public interface DimensioningRepository {

    Dimensioning save(Dimensioning study);

    Optional<Dimensioning> findById(UUID id);
}
