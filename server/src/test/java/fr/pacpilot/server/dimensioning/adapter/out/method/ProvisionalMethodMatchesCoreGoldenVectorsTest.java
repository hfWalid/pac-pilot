package fr.pacpilot.server.dimensioning.adapter.out.method;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.dimensioning.model.ConstructionPeriod;
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome;
import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.dimensioning.model.InputsSnapshot;
import fr.pacpilot.core.dimensioning.model.InsulationLevel;
import fr.pacpilot.core.dimensioning.model.RefusalReason;
import fr.pacpilot.core.dimensioning.model.VentilationType;
import fr.pacpilot.core.dimensioning.engine.DimensioningEngine;
import fr.pacpilot.core.dimensioning.port.RunDimensioning;
import fr.pacpilot.core.shared.CeilingHeightM;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
import fr.pacpilot.core.shared.SurfaceM2;
import fr.pacpilot.core.shared.TemperatureC;
import org.junit.jupiter.api.Test;

/**
 * Binds the server's Java provisional formula set to {@code :core}'s Kotlin one, through the only
 * thing that can be checked from this side: the published golden vectors.
 *
 * <p>The two sets exist separately for a reason — {@code :core} keeps no shippable placeholder
 * (M2-07), while ADR-0015 lets the server boot with one — and two hand-copied tables of magic
 * numbers is exactly the arrangement that drifts. Copying is not verified by reading carefully; it
 * is verified by reproducing published expectations.
 *
 * <p>Every value asserted below is quoted from {@code core/src/commonTest/vectors/dimensioning.vectors}
 * by vector id. If the server's mirror is edited and the core's is not, this fails and names the
 * vector. If the M2 gate lands a real method, these vectors stay — they describe what the
 * provisional method produced — and this test is deleted along with the class it guards.
 */
class ProvisionalMethodMatchesCoreGoldenVectorsTest {

    /**
     * The engine over the server's provisional set, built directly rather than through Spring. This
     * test is about arithmetic, so a context — and the database one would drag in — would only add
     * ways for it to fail for reasons that are not the subject. The wiring itself is
     * {@link DimensioningMethodConfigurationTest}'s.
     */
    private final RunDimensioning dimensioning = new DimensioningEngine(new ProvisionalFormulaSetProvider());

    /** The devis date is irrelevant to a provisional method, but the port requires one stated. */
    private static final EffectiveDate ANY_DATE = new EffectiveDate(2026, 8, 22);

    private static InputsSnapshot snapshot(
            int surfaceCentiSquareMetres,
            int ceilingHeightCentimetres,
            ConstructionPeriod period,
            InsulationLevel insulation,
            VentilationType ventilation,
            EmitterType emitter,
            int baseTemperatureDeciC) {
        return new InputsSnapshot(
                new SurfaceM2(surfaceCentiSquareMetres),
                new CeilingHeightM(ceilingHeightCentimetres),
                period,
                insulation,
                ventilation,
                emitter,
                ClimateZone.H1,
                new TemperatureC(baseTemperatureDeciC),
                new TemperatureC(190),
                new ElectricalSupplyKva(9));
    }

    private DimensioningOutcome.Computed computed(InputsSnapshot inputs) {
        DimensioningOutcome outcome = dimensioning.run(inputs, ANY_DATE);
        assertThat(outcome).isInstanceOf(DimensioningOutcome.Computed.class);
        return (DimensioningOutcome.Computed) outcome;
    }

    @Test
    void reproducesVectorDimensioningPeriodBefore1975() {
        // dimensioning-period-before-1975-001
        var result =
                computed(
                                snapshot(
                                        12_000,
                                        250,
                                        ConstructionPeriod.BEFORE_1975,
                                        InsulationLevel.PARTIAL,
                                        VentilationType.VMC_SIMPLE_FLUX,
                                        EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                        -70))
                        .getResult();

        assertThat(result.getHeatLoad().render()).isEqualTo("19.032");
        assertThat(result.getRecommendedPowerBand().getMinimum().render()).isEqualTo("17.129");
        assertThat(result.getRecommendedPowerBand().getMaximum().render()).isEqualTo("22.838");
        assertThat(result.getRecommendedFlowTemperature().render()).isEqualTo("50.0");
        assertThat(result.getConfidence().name()).isEqualTo("INDICATIVE");
    }

    @Test
    void reproducesVectorDimensioningPeriodFrom1975To1989() {
        // dimensioning-period-from-1975-to-1989-001
        var result =
                computed(
                                snapshot(
                                        12_000,
                                        250,
                                        ConstructionPeriod.FROM_1975_TO_1989,
                                        InsulationLevel.PARTIAL,
                                        VentilationType.VMC_SIMPLE_FLUX,
                                        EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                        -70))
                        .getResult();

        assertThat(result.getHeatLoad().render()).isEqualTo("22.152");
        assertThat(result.getRecommendedPowerBand().getMinimum().render()).isEqualTo("19.937");
        assertThat(result.getRecommendedPowerBand().getMaximum().render()).isEqualTo("26.582");
    }

    @Test
    void reproducesVectorDimensioningEnvelopeUpperEdge() {
        // dimensioning-envelope-upper-edge-001 — the far corner of the envelope, where a copied
        // coefficient that is wrong in the third decimal shows up largest.
        var result =
                computed(
                                snapshot(
                                        30_000,
                                        350,
                                        ConstructionPeriod.AFTER_2012,
                                        InsulationLevel.GOOD,
                                        VentilationType.VMC_DOUBLE_FLUX,
                                        EmitterType.UNDERFLOOR_HEATING,
                                        0))
                        .getResult();

        assertThat(result.getHeatLoad().render()).isEqualTo("89.490");
        assertThat(result.getRecommendedPowerBand().getMinimum().render()).isEqualTo("80.541");
        assertThat(result.getRecommendedPowerBand().getMaximum().render()).isEqualTo("107.388");
        assertThat(result.getRecommendedFlowTemperature().render()).isEqualTo("30.0");
    }

    @Test
    void reproducesVectorDimensioningRefusalSurface() {
        // dimensioning-refusal-surface-001 — the envelope must be mirrored too, not only the
        // coefficients. A wider envelope here would compute where the core refuses.
        DimensioningOutcome outcome =
                dimensioning.run(
                        snapshot(
                                1_000,
                                250,
                                ConstructionPeriod.BEFORE_1975,
                                InsulationLevel.PARTIAL,
                                VentilationType.VMC_SIMPLE_FLUX,
                                EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                -70),
                        ANY_DATE);

        assertThat(outcome).isInstanceOf(DimensioningOutcome.ManualStudyRequired.class);
        assertThat(((DimensioningOutcome.ManualStudyRequired) outcome).getReasons())
                .containsExactly(RefusalReason.SURFACE_OUTSIDE_RANGE);
    }

    @Test
    void reproducesVectorDimensioningRefusalMultiple() {
        // dimensioning-refusal-multiple-001
        DimensioningOutcome outcome =
                dimensioning.run(
                        snapshot(
                                50_000,
                                400,
                                ConstructionPeriod.BEFORE_1975,
                                InsulationLevel.PARTIAL,
                                VentilationType.VMC_SIMPLE_FLUX,
                                EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                -300),
                        ANY_DATE);

        assertThat(outcome).isInstanceOf(DimensioningOutcome.ManualStudyRequired.class);
        assertThat(((DimensioningOutcome.ManualStudyRequired) outcome).getReasons())
                .containsExactly(
                        RefusalReason.SURFACE_OUTSIDE_RANGE,
                        RefusalReason.CEILING_HEIGHT_OUTSIDE_RANGE,
                        RefusalReason.BASE_TEMPERATURE_OUTSIDE_RANGE);
    }

    @Test
    void withholdsFlowTemperatureForFanCoilJustAsTheCoreDoes() {
        // The withheld path. A study still stands; only the loi d'eau guidance is declined.
        var result =
                computed(
                                snapshot(
                                        12_000,
                                        250,
                                        ConstructionPeriod.BEFORE_1975,
                                        InsulationLevel.PARTIAL,
                                        VentilationType.VMC_SIMPLE_FLUX,
                                        EmitterType.FAN_COIL,
                                        -70))
                        .getResult();

        assertThat(result.getRecommendedFlowTemperature()).isNull();
        assertThat(result.getHeatLoad().render()).isEqualTo("19.032");
    }
}
