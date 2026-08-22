package fr.pacpilot.core.dimensioning.engine

import fr.pacpilot.core.dimensioning.model.AssumptionsLog
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome
import fr.pacpilot.core.dimensioning.model.HeatLoadResult
import fr.pacpilot.core.dimensioning.model.InputsSnapshot
import fr.pacpilot.core.dimensioning.port.FlowTemperatureGuidance
import fr.pacpilot.core.dimensioning.port.FormulaSet
import fr.pacpilot.core.dimensioning.port.FormulaSetProvider
import fr.pacpilot.core.dimensioning.port.RunDimensioning
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.PowerBand
import fr.pacpilot.core.shared.PowerKw
import fr.pacpilot.core.shared.TemperatureC

/**
 * The simplified heat-loss method, behind [RunDimensioning].
 *
 * **Structurally correct before numerically correct.** Not one coefficient is chosen here — every
 * number arrives through the injected [FormulaSet], including the volumetric heat capacity of air
 * and the envelope-area factor, which are physical and structural respectively but are still
 * numbers and so still need a source (`CLAUDE.md` §12). That is what lets this engine be written,
 * reviewed and shipped while the ⚑ gate is still open: closing the gate lands values, not a rewrite.
 *
 * **The structural form is itself provisional.** Splitting losses into a transmission term and an
 * air-renewal term, and inferring envelope area from floor area rather than surveying the walls, are
 * simplifications — the ones that make a 15-minute pre-visit possible, and the ones an auditor would
 * question first. The ⚑ gate (PAC-42) validates the shape as well as the numbers; if it rejects the
 * shape, this file changes with it.
 *
 * Deterministic by construction: no clock, no randomness, and the effective date is a parameter
 * that selects which version of the method applies. Both are enforced by ArchUnit rather than by
 * convention.
 */
class DimensioningEngine(private val formulaSets: FormulaSetProvider) : RunDimensioning {

    override fun run(inputs: InputsSnapshot, effectiveDate: EffectiveDate): DimensioningOutcome {
        val formulaSet = formulaSets.formulaSetOn(effectiveDate)

        // The envelope check runs first and short-circuits: a dwelling the method was never
        // validated for must not have a single coefficient read against it, let alone a number
        // produced (PRODUCT-VIEWS #9).
        val violations = formulaSet.envelope.violationsFor(inputs)
        if (violations.isNotEmpty()) return DimensioningOutcome.ManualStudyRequired(violations)

        val recording = RecordingFormulaSet(formulaSet)
        val heatLoad = heatLoadFor(inputs, recording)

        return DimensioningOutcome.Computed(
            HeatLoadResult(
                heatLoad = heatLoad,
                recommendedPowerBand = powerBandFor(heatLoad, recording),
                recommendedFlowTemperature = flowTemperatureFor(inputs, recording),
                assumptions = AssumptionsLog(recording.assumptions),
            ),
        )
    }

    /**
     * Transmission losses through the envelope plus the losses from renewing the air, both across
     * the gap between the indoor target and the outdoor design temperature.
     *
     * The gap is a [fr.pacpilot.core.shared.TemperatureDifferenceC], not a [TemperatureC]: 19 °C is
     * a position on the scale and the 26 K gap to −7 °C is a magnitude, and passing one where the
     * other belongs compiles and yields a plausible wrong answer.
     */
    private fun heatLoadFor(inputs: InputsSnapshot, formulaSet: FormulaSet): PowerKw {
        val temperatureGap = (inputs.targetIndoorTemperature - inputs.baseTemperature).magnitude

        val uValue = formulaSet.uValueFor(inputs.constructionPeriod, inputs.insulationLevel)
        val envelopeArea = inputs.surface.magnitude * formulaSet.envelopeAreaFactor().value.magnitude
        val transmissionWatts = uValue.value.magnitude * envelopeArea * temperatureGap

        val heatCapacity = formulaSet.airVolumetricHeatCapacity().value.magnitude
        val airChangeRate = formulaSet.airChangeRateFor(inputs.ventilationType).value.magnitude
        val heatedVolume = inputs.surface.magnitude * inputs.ceilingHeight.magnitude
        val ventilationWatts = heatCapacity * airChangeRate * heatedVolume * temperatureGap

        return PowerKw(roundToWatts(transmissionWatts + ventilationWatts))
    }

    /**
     * The range a machine may be selected in — asymmetric, because the two failure modes are not
     * symmetric: under-sizing leaves a cold client in February, over-sizing short-cycles the machine
     * to an early death (PRODUCT-VIEWS #2, J1).
     *
     * The band always contains the heat load it came from, since both margins are non-negative
     * proportions of it.
     */
    private fun powerBandFor(heatLoad: PowerKw, formulaSet: FormulaSet): PowerBand {
        val under = formulaSet.underSizingMargin().value.applyTo(heatLoad)
        val over = formulaSet.overSizingMargin().value.applyTo(heatLoad)
        return PowerBand(
            minimum = PowerKw(heatLoad.watts - under.watts),
            maximum = PowerKw(heatLoad.watts + over.watts),
        )
    }

    /**
     * Loi d'eau guidance, or nothing at all where the method has none validated for these emitters.
     *
     * A withheld guidance does **not** block the study — the dwelling is inside the envelope and the
     * heat load stands. The assumptions log records that the method declined, so an installer
     * reading the devis can tell the difference between "no advice" and "advice forgotten".
     */
    private fun flowTemperatureFor(inputs: InputsSnapshot, formulaSet: FormulaSet): TemperatureC? =
        when (val guidance = formulaSet.flowTemperatureFor(inputs.emitterType)) {
            is FlowTemperatureGuidance.Advised -> guidance.flowTemperature
            is FlowTemperatureGuidance.Withheld -> null
        }

    /**
     * The single rounding site in this engine, on purpose.
     *
     * Two rounding sites is how the installer's phone and the server end up one watt apart, which
     * surfaces as a divergence flag in front of a homeowner rather than as a failing test.
     *
     * Written with integer comparisons rather than a library rounding call because the tie-breaking
     * behaviour of the platform functions is not guaranteed to match between the JVM and a browser,
     * and this project's whole correctness contract is that the two agree to the character. Heat
     * loads are non-negative, so half-up and half-away-from-zero coincide here.
     */
    private fun roundToWatts(watts: Double): Int {
        val truncated = watts.toInt()
        val remainder = watts - truncated
        return if (remainder * 2 >= 1) truncated + 1 else truncated
    }
}
