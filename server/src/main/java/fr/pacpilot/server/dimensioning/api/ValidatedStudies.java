package fr.pacpilot.server.dimensioning.api;

import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import java.util.Optional;
import java.util.UUID;

/**
 * The Dimensioning context's exposed surface — the one way another context reaches a study.
 *
 * <p>Deliberately narrower than {@code DimensioningRepository}, which is this context's own driven
 * port and stays internal. What a devis needs is not "a study" but specifically a <b>signed</b> one:
 * {@code ARCHITECTURE} #7 allows only {@code Validated → Quoted}, and returning the sealed
 * supertype here would push that check onto every caller and make forgetting it possible.
 *
 * <p>Returning {@link ValidatedDimensioning} rather than a quoting-side copy is not a boundary
 * violation: the type belongs to {@code :core}, which both contexts already depend on, and
 * duplicating it server-side would create a second definition of the aggregate the whole
 * client/server correctness contract is written against.
 */
public interface ValidatedStudies {

    /**
     * The signed study with this id, or empty when there is none — including when a study exists but
     * has not been signed. A caller cannot tell those apart, and should not: neither is a devis it
     * may be built on.
     */
    Optional<ValidatedDimensioning> findValidated(UUID id);
}
