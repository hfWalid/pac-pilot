package fr.pacpilot.core.dimensioning.engine

import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.ValidatedEnvelope
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.dimensioning.port.FlowTemperatureGuidance
import fr.pacpilot.core.dimensioning.port.FormulaSet
import fr.pacpilot.core.dimensioning.port.FormulaSetProvider
import fr.pacpilot.core.dimensioning.port.Sourced
import fr.pacpilot.core.shared.AirChangeRate
import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.EnvelopeAreaFactor
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC
import fr.pacpilot.core.shared.ThermalTransmittance
import fr.pacpilot.core.shared.VolumetricHeatCapacity

private const val TBD = "SOURCE_TBD"

/**
 * A formula set with no validated content whatsoever, so M2 can be built before the ⚑ gate.
 *
 * ## Why this lives in `commonMain` (ADR-0021)
 *
 * It began in `commonTest`, where M2-07 put it so that shipping it was impossible by construction.
 * That was right while nothing outside the tests needed it — and its own comment predicted the
 * pressure exactly: *":server at M4 and the PWA at M7 will have nothing to boot against and will
 * each need a formula set of their own."*
 *
 * Both arrived. Keeping it in `commonTest` would have meant **three copies of these numbers** — a
 * Java mirror in `:server`, a Kotlin one for the JS target, and this — each verified separately
 * against the same golden vectors. Three tables of magic numbers agree only as long as three people
 * keep them agreeing, and a server computing 19,032 W where the tablet computed 19,031 W surfaces as
 * a divergence flag in front of a homeowner. That is the failure the one-source-two-targets bet
 * exists to prevent.
 *
 * **What protects this was never the placement.** It is that the numbers are visibly absurd, that
 * nothing wires this set automatically, and that every result it produces reports `INDICATIVE`. All
 * three travel with the class wherever it lives.
 *
 * **When PAC-42 closes, delete this class rather than editing it.** A validated method arrives as
 * its own implementation; leaving this one selectable afterwards recreates the risk M2-07 named.
 * And before writing that implementation, read ADR-0021's closing section: `CLAUDE.md` §4.4 says
 * thermal coefficients were always meant to be *published packs*, not code.
 *
 * ## Why the numbers look wrong
 *
 * They are deliberately non-physical. The U-values *rise* with newer construction, which is
 * backwards; the air-change rate *rises* with better ventilation, also backwards; air's volumetric
 * heat capacity is 1.000 rather than roughly 0.34. Nobody can mistake any of these for a barème,
 * and no reviewer glancing at a result will assume it means anything. Their only job is to exercise
 * the arithmetic and let the golden vectors bind the two targets together.
 *
 * Every one carries `SOURCE_TBD`, so every result computed here reports `INDICATIVE` confidence.
 */
class ProvisionalFormulaSet : FormulaSet {

    /**
     * Deliberately narrow. A method that has been validated for nothing should refuse readily —
     * PRODUCT-VIEWS #11 makes refusing the mitigation, not the limitation.
     */
    override val envelope: ValidatedEnvelope = ValidatedEnvelope(
        minimumSurface = SurfaceM2.ofWholeSquareMetres(20),
        maximumSurface = SurfaceM2.ofWholeSquareMetres(300),
        minimumCeilingHeight = CeilingHeightM(200),
        maximumCeilingHeight = CeilingHeightM(350),
        minimumBaseTemperature = TemperatureC.ofWholeDegrees(-20),
        maximumBaseTemperature = TemperatureC.ofWholeDegrees(0),
        coveredConstructionPeriods = ConstructionPeriod.entries.toSet(),
        coveredInsulationLevels = InsulationLevel.entries.toSet(),
        coveredVentilationTypes = VentilationType.entries.toSet(),
        coveredEmitterTypes = EmitterType.entries.toSet(),
    )

    override fun uValueFor(
        period: ConstructionPeriod,
        insulation: InsulationLevel,
    ): Sourced<ThermalTransmittance> = Sourced(
        ThermalTransmittance((period.ordinal + 1) * 1_000 + insulation.ordinal * 100),
        "$TBD (arithmetic ramp, not a U-value; rises with newer construction, which is backwards)",
    )

    override fun airChangeRateFor(ventilation: VentilationType): Sourced<AirChangeRate> = Sourced(
        AirChangeRate((ventilation.ordinal + 1) * 1_000),
        "$TBD (arithmetic ramp; rises with better ventilation, which is backwards)",
    )

    override fun airVolumetricHeatCapacity(): Sourced<VolumetricHeatCapacity> = Sourced(
        VolumetricHeatCapacity(1_000),
        "$TBD (unity, not the physical value of air)",
    )

    override fun envelopeAreaFactor(): Sourced<EnvelopeAreaFactor> = Sourced(
        EnvelopeAreaFactor(1_000),
        "$TBD (unity; a real dwelling loses heat through more envelope than it has floor)",
    )

    override fun underSizingMargin(): Sourced<Percentage> = Sourced(
        Percentage(1_000),
        "$TBD (round placeholder, not a validated tolerance)",
    )

    override fun overSizingMargin(): Sourced<Percentage> = Sourced(
        Percentage(2_000),
        "$TBD (round placeholder, deliberately different from the under margin)",
    )

    /**
     * `FAN_COIL` is withheld on purpose: the withheld path has to be reachable, and a method with
     * nothing validated is exactly the one that should decline for the least common emitter.
     */
    override fun flowTemperatureFor(emitter: EmitterType): FlowTemperatureGuidance = when (emitter) {
        EmitterType.RADIATOR_HIGH_TEMPERATURE ->
            FlowTemperatureGuidance.Advised(TemperatureC(500), "$TBD (round placeholder)")
        EmitterType.RADIATOR_LOW_TEMPERATURE ->
            FlowTemperatureGuidance.Advised(TemperatureC(400), "$TBD (round placeholder)")
        EmitterType.UNDERFLOOR_HEATING ->
            FlowTemperatureGuidance.Advised(TemperatureC(300), "$TBD (round placeholder)")
        EmitterType.FAN_COIL ->
            FlowTemperatureGuidance.Withheld("$TBD (no guidance validated for this emitter)")
    }
}

/** Hands the same provisional set back whatever the date — there is only one, and it is not real. */
class ProvisionalFormulaSetProvider(
    private val formulaSet: FormulaSet = ProvisionalFormulaSet(),
) : FormulaSetProvider {

    override fun formulaSetOn(effectiveDate: EffectiveDate): FormulaSet = formulaSet
}
