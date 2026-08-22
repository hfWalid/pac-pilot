package fr.pacpilot.server.interventions.api;

import java.time.Instant;
import java.util.UUID;

/**
 * The Interventions context's erasure surface.
 *
 * <p>Exists because {@code address_snapshot} is denormalised personal data living in this context's
 * table while the erasure request arrives at Dossier. Dossier cannot reach in — that wall is
 * enforced by {@code BoundedContextRulesTest} — so this is the door it knocks on.
 *
 * <p>Discovered by test rather than by design review: PAC-60's sweep passed while this table had no
 * rows, and the first intervention written to it made the address survive an erasure.
 */
public interface InterventionErasure {

    /**
     * Severs the recorded address on every visit at a site.
     *
     * @return how many visits were severed, so a caller can report what happened rather than assume.
     */
    int severAddressesForSite(UUID siteId, Instant at);
}
