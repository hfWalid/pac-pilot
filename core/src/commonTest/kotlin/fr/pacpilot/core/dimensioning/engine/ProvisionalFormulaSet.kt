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
 * ## Why this lives in `commonTest`
 *
 * The decision PAC-40 asked to be made explicitly. A placeholder formula set is exactly the
 * artefact that quietly becomes authoritative — wired into a demo, the demo becomes a pilot, and a
 * `SOURCE_TBD` coefficient reaches a homeowner. `CLAUDE.md` §12 calls an unsourced number that
 * looks authoritative the failure mode this project cannot afford.
 *
 * `commonTest` makes shipping it **impossible by construction** rather than merely discouraged: it
 * is not on the production classpath of either target, so no guard, naming convention or build
 * check is needed to enforce what the module boundary already enforces.
 *
 * **The cost, stated:** `:server` at M4 and the PWA at M7 will have nothing to boot against and
 * will each need a formula set of their own. That is the right moment to decide what a
 * pre-gate deployment should do — most likely refuse to start rather than compute with placeholders
 * — and it is a decision better made then than pre-empted here by leaving something shippable lying
 * around.
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
