package fr.pacpilot.server.dimensioning.application;

import fr.pacpilot.core.dimensioning.model.ComputedDimensioning;
import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome;
import fr.pacpilot.core.dimensioning.model.InputsSnapshot;
import fr.pacpilot.core.dimensioning.port.RunDimensioning;
import fr.pacpilot.core.shared.DimensioningId;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.InstallerId;
import fr.pacpilot.core.shared.InstantUtc;
import fr.pacpilot.core.shared.SiteId;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a study and records it if the method answered.
 *
 * <p>The one rule that lives here rather than in the engine or the controller: <b>a refusal is not
 * persisted</b>. {@code ManualStudyRequired} means the dwelling fell outside the validated envelope,
 * so there is no result to reproduce, nothing for an installer to sign, and nothing an auditor could
 * ask about. {@code Dimensioning.computed} will not even accept one — this is where that becomes a
 * decision about storage rather than a compile error.
 */
@Service
public class RunStudy {

    private final RunDimensioning engine;
    private final DimensioningRepository studies;
    private final Clock clock;

    RunStudy(RunDimensioning engine, DimensioningRepository studies, Clock clock) {
        this.engine = engine;
        this.studies = studies;
        this.clock = clock;
    }

    /** The outcome, plus the id it was stored under when it was stored at all. */
    public record Outcome(DimensioningOutcome outcome, UUID storedAs) {}

    @Transactional
    public Outcome run(UUID studyId, UUID siteId, InputsSnapshot inputs, EffectiveDate effectiveDate) {
        DimensioningOutcome outcome = engine.run(inputs, effectiveDate);

        if (!(outcome instanceof DimensioningOutcome.Computed computed)) {
            return new Outcome(outcome, null);
        }

        ComputedDimensioning study =
                Dimensioning.Companion.computed(
                        new DimensioningId(studyId.toString()),
                        new SiteId(siteId.toString()),
                        inputs,
                        computed,
                        effectiveDate);
        studies.save(study);
        return new Outcome(outcome, studyId);
    }

    /**
     * The artisan takes responsibility for a computed study.
     *
     * <p>Its own operation, not a field update, because it is a distinct act with distinct legal
     * weight ({@code CLAUDE.md} §4.5). The domain enforces the rest: {@code validate} exists only on
     * the computed case, so re-signing is unsayable rather than refused.
     */
    @Transactional
    public Dimensioning validate(UUID studyId, UUID installerId) {
        Dimensioning stored =
                studies.findById(studyId)
                        .orElseThrow(() -> new IllegalArgumentException("no study " + studyId));

        if (!(stored instanceof ComputedDimensioning computed)) {
            throw new IllegalStateException(
                    "study " + studyId + " is already signed; re-deciding means computing a new study");
        }

        return studies.save(
                computed.validate(
                        new InstallerId(installerId.toString()),
                        new InstantUtc(clock.instant().toEpochMilli())));
    }
}
