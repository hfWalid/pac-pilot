package fr.pacpilot.server.dimensioning.adapter.out.method;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.dimensioning.port.FormulaSetProvider;
import fr.pacpilot.core.dimensioning.port.RunDimensioning;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * ADR-0015, asserted as behaviour rather than trusted as prose.
 *
 * <p>{@link ApplicationContextRunner} rather than {@code @SpringBootTest} deliberately: it starts
 * with <b>no property sources at all</b>, which is the only way to prove that a missing
 * {@code pacpilot.dimensioning.method} actually fails. A full context reads {@code application.yml}
 * and any test defaults, so the absent case could never be observed there — and the absent case is
 * the whole mechanism.
 */
class DimensioningMethodConfigurationTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(DimensioningMethodConfiguration.class);

    @Test
    void aServerStartedWithoutChoosingAMethodRefusesToStart() {
        // Property 1 of ADR-0015. No default value anywhere: not in the @Value placeholder, not in
        // application.yml. Indicative mode is reached only by someone who wrote the word.
        context.run(
                started ->
                        assertThat(started)
                                .hasFailed()
                                .getFailure()
                                .hasStackTraceContaining("pacpilot.dimensioning.method"));
    }

    @Test
    void choosingIndicativeModeWiresTheProvisionalMethod() {
        context.withPropertyValues("pacpilot.dimensioning.method=indicative-provisional")
                .run(
                        started -> {
                            assertThat(started).hasNotFailed();
                            assertThat(started).hasSingleBean(FormulaSetProvider.class);
                            assertThat(started).hasSingleBean(RunDimensioning.class);
                            assertThat(started.getBean(FormulaSetProvider.class))
                                    .isInstanceOf(ProvisionalFormulaSetProvider.class);
                        });
    }

    @Test
    void choosingTheValidatedMethodFailsAndNamesTheGateThatWouldProvideIt() {
        // The value an operator will eventually set. Until PAC-42 closes there is nothing behind it,
        // and the failure has to say so — an unknown-enum stack trace would send someone looking for
        // a typo rather than for the gate.
        context.withPropertyValues("pacpilot.dimensioning.method=validated")
                .run(
                        started ->
                                assertThat(started)
                                        .hasFailed()
                                        .getFailure()
                                        .hasStackTraceContaining("PAC-42")
                                        .hasStackTraceContaining("indicative-provisional"));
    }

    @Test
    void anUnrecognisedMethodIsRefusedRatherThanFallingBackToOne() {
        // The failure mode this guards is a typo silently selecting the safest-looking option. There
        // is no fallback: an unbindable value fails the context.
        context.withPropertyValues("pacpilot.dimensioning.method=whatever-looks-plausible")
                .run(started -> assertThat(started).hasFailed());
    }

    @Test
    void everyCoefficientTheProvisionalMethodSuppliesIsMarkedUnsourced() {
        // Property 3 of ADR-0015, at its root. Confidence.INDICATIVE is derived from whether the
        // applied coefficients are sourced (M2-06), so a single coefficient that looked cited would
        // silently promote a result to SUPPORTED. Checked here rather than assumed from the result.
        var formulaSet = new ProvisionalFormulaSetProvider().formulaSetOn(new fr.pacpilot.core.shared.EffectiveDate(2026, 8, 22));

        assertThat(formulaSet.airVolumetricHeatCapacity().isProvisional()).isTrue();
        assertThat(formulaSet.envelopeAreaFactor().isProvisional()).isTrue();
        assertThat(formulaSet.underSizingMargin().isProvisional()).isTrue();
        assertThat(formulaSet.overSizingMargin().isProvisional()).isTrue();

        for (var period : fr.pacpilot.core.dimensioning.model.ConstructionPeriod.values()) {
            for (var insulation : fr.pacpilot.core.dimensioning.model.InsulationLevel.values()) {
                assertThat(formulaSet.uValueFor(period, insulation).isProvisional())
                        .as("U-value for %s / %s", period, insulation)
                        .isTrue();
            }
        }
        for (var ventilation : fr.pacpilot.core.dimensioning.model.VentilationType.values()) {
            assertThat(formulaSet.airChangeRateFor(ventilation).isProvisional())
                    .as("air-change rate for %s", ventilation)
                    .isTrue();
        }
        for (var emitter : fr.pacpilot.core.dimensioning.model.EmitterType.values()) {
            assertThat(formulaSet.flowTemperatureFor(emitter).getSource()).startsWith("SOURCE_TBD");
        }
    }
}
