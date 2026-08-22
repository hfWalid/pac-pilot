package fr.pacpilot.server.dimensioning.application.port.out;

import fr.pacpilot.server.dimensioning.api.VerificationVerdict;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One verification run, as it is stored.
 *
 * <p>A row per run rather than per study: a study verified again after a method correction has two
 * verdicts and both are interesting. The history is the point — it is what shows whether a
 * divergence appeared, when, and against which version.
 */
public record VerificationRecord(
        UUID id,
        UUID studyId,
        Instant verifiedAt,
        String outcome,
        Optional<String> differences,
        Optional<String> reason) {

    public static final String MATCHED = "MATCHED";
    public static final String DIVERGED = "DIVERGED";
    public static final String NOT_VERIFIABLE = "NOT_VERIFIABLE";

    /** Flattens the verdict for storage. The check constraints in V7 mirror these pairings. */
    public static VerificationRecord of(
            UUID id, UUID studyId, Instant verifiedAt, VerificationVerdict verdict) {
        return switch (verdict) {
            case VerificationVerdict.Matched ignored ->
                    new VerificationRecord(id, studyId, verifiedAt, MATCHED, Optional.empty(), Optional.empty());
            case VerificationVerdict.Diverged diverged ->
                    new VerificationRecord(
                            id, studyId, verifiedAt, DIVERGED, Optional.of(diverged.render()), Optional.empty());
            case VerificationVerdict.NotVerifiable notVerifiable ->
                    new VerificationRecord(
                            id,
                            studyId,
                            verifiedAt,
                            NOT_VERIFIABLE,
                            Optional.empty(),
                            Optional.of(notVerifiable.reason()));
        };
    }
}
