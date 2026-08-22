package fr.pacpilot.server.dimensioning.api;

import java.util.UUID;

/**
 * The Dimensioning context's verification surface.
 *
 * <p>Recomputes a stored study from its own inputs and its own recorded effective date, and records
 * what it found. Wired at M4 although sync does not arrive until M8: exercising the
 * recompute-and-compare path through the API first turns M8 into an integration problem rather than
 * a discovery problem.
 */
public interface StudyVerification {

    /**
     * Recomputes the study and persists the verdict.
     *
     * @throws IllegalArgumentException when no study with this id exists — verifying something that
     *     is not there is a caller error, not a divergence.
     */
    VerificationVerdict verify(UUID studyId);
}
