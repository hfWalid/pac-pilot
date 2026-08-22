package fr.pacpilot.server.dimensioning.application;

import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome;
import fr.pacpilot.core.dimensioning.model.HeatLoadResult;
import fr.pacpilot.core.dimensioning.port.RunDimensioning;
import fr.pacpilot.server.dimensioning.api.StudyVerification;
import fr.pacpilot.server.dimensioning.api.VerificationVerdict;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
import fr.pacpilot.server.dimensioning.application.port.out.VerificationRecord;
import fr.pacpilot.server.dimensioning.application.port.out.VerificationRecordRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes a stored study with the JVM core and records whether it agrees.
 *
 * <p>The guarantee of {@code CLAUDE.md} §4.2: the client computes for offline UX, the server
 * recomputes from the stored inputs and the recorded version and asserts equality. The golden
 * vectors already prove that the JVM and JS targets agree on the same inputs; this proves the same
 * across <i>processes</i>, which is a different claim — it catches a device that sent a result it
 * did not compute from the inputs it also sent.
 *
 * <p><b>Recomputation uses the study's own effective date, never today's.</b> That is what makes the
 * check meaningful after a method correction: a study computed under last year's formula set is
 * recomputed under last year's formula set, so a divergence means the arithmetic disagreed rather
 * than that the method moved.
 *
 * <p>Comparison is on <i>rendered</i> values. The unit types hold fixed-point integers and render
 * deterministically, so an exact string comparison is meaningful here in a way floating-point
 * equality never is — and the rendered form is also what a human reads on the devis.
 */
@Service
class StudyVerifier implements StudyVerification {

    private final DimensioningRepository studies;
    private final VerificationRecordRepository records;
    private final RunDimensioning engine;
    private final Clock clock;

    StudyVerifier(
            DimensioningRepository studies,
            VerificationRecordRepository records,
            RunDimensioning engine,
            Clock clock) {
        this.studies = studies;
        this.records = records;
        this.engine = engine;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VerificationVerdict verify(UUID studyId) {
        Dimensioning stored =
                studies.findById(studyId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "no study " + studyId + " to verify"));

        VerificationVerdict verdict = compare(stored);
        records.save(VerificationRecord.of(UUID.randomUUID(), studyId, clock.instant(), verdict));
        return verdict;
    }

    private VerificationVerdict compare(Dimensioning stored) {
        DimensioningOutcome recomputed = engine.run(stored.getInputs(), stored.getEffectiveDate());

        // A refusal on recomputation is not a divergence in the arithmetic — it means the method's
        // validated envelope no longer covers inputs it once did, which is a method change and needs
        // a person, not a flag on this study.
        if (!(recomputed instanceof DimensioningOutcome.Computed computed)) {
            return new VerificationVerdict.NotVerifiable(
                    "the method now refuses these inputs, so there is nothing to compare against; "
                            + "the validated envelope changed since the study was computed");
        }

        List<VerificationVerdict.FieldDifference> differences =
                differencesBetween(stored.getResult(), computed.getResult());
        return differences.isEmpty()
                ? new VerificationVerdict.Matched()
                : new VerificationVerdict.Diverged(differences);
    }

    /**
     * Every figure a devis depends on, compared field by field.
     *
     * <p>The assumptions log is deliberately not compared: it records what the method applied, so it
     * moves whenever the formula set's provenance strings change, which is not an arithmetic
     * disagreement. What matters is whether the numbers came out the same.
     */
    private static List<VerificationVerdict.FieldDifference> differencesBetween(
            HeatLoadResult stored, HeatLoadResult recomputed) {
        List<VerificationVerdict.FieldDifference> differences = new ArrayList<>();

        compare(differences, "heatLoad", stored.getHeatLoad().render(), recomputed.getHeatLoad().render());
        compare(
                differences,
                "powerBand.minimum",
                stored.getRecommendedPowerBand().getMinimum().render(),
                recomputed.getRecommendedPowerBand().getMinimum().render());
        compare(
                differences,
                "powerBand.maximum",
                stored.getRecommendedPowerBand().getMaximum().render(),
                recomputed.getRecommendedPowerBand().getMaximum().render());
        compare(
                differences,
                "flowTemperature",
                renderOrWithheld(stored),
                renderOrWithheld(recomputed));
        compare(
                differences,
                "confidence",
                stored.getConfidence().name(),
                recomputed.getConfidence().name());

        return differences;
    }

    private static String renderOrWithheld(HeatLoadResult result) {
        return result.getRecommendedFlowTemperature() == null
                ? "withheld"
                : result.getRecommendedFlowTemperature().render();
    }

    private static void compare(
            List<VerificationVerdict.FieldDifference> differences,
            String field,
            String stored,
            String recomputed) {
        if (!stored.equals(recomputed)) {
            differences.add(new VerificationVerdict.FieldDifference(field, stored, recomputed));
        }
    }
}
