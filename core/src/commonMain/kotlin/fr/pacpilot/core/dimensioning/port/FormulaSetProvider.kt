package fr.pacpilot.core.dimensioning.port

import fr.pacpilot.core.dimensioning.model.Assumption
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.ValidatedEnvelope
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.shared.AirChangeRate
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.EnvelopeAreaFactor
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.TemperatureC
import fr.pacpilot.core.shared.ThermalTransmittance
import fr.pacpilot.core.shared.VolumetricHeatCapacity

/**
 * A coefficient and where it came from, inseparably.
 *
 * There is no way to obtain a coefficient without its provenance, and that is the point. The
 * assumptions log (M1-05) and the QualiPAC audit chain both depend on being able to say where a
 * number came from; an accessor returning a bare value would force the engine to maintain a
 * parallel list of what it used, which is correct on the day it is written and wrong on the first
 * change nobody mirrors.
 *
 * [source] is a citation or [Assumption.SOURCE_TBD] with a reason — the same marker the assumptions
 * log and the golden vectors already use, so provisional-ness travels end to end without
 * translation.
 */
data class Sourced<T>(val value: T, val source: String) {

    init {
        require(source.isNotBlank()) {
            "a coefficient cites its source or says plainly it has none (${Assumption.SOURCE_TBD})"
        }
    }

    val isProvisional: Boolean get() = source.startsWith(Assumption.SOURCE_TBD)

    /** The assumption this coefficient contributes to the log, in the reviewer's language. */
    fun asAssumption(statement: String): Assumption = Assumption(statement, source)
}

/**
 * What the method advises about the loi d'eau, or its refusal to advise.
 *
 * Sealed rather than a nullable temperature because *withholding* guidance is itself a finding an
 * installer must see: the tool declined, it did not forget. [Withheld] therefore still carries a
 * source, so the assumptions log can record why nothing was advised.
 *
 * Distinct from an out-of-envelope refusal. A dwelling can be squarely inside the envelope while
 * the method still has nothing validated to say about its emitters — that must not block the study.
 */
sealed interface FlowTemperatureGuidance {

    val source: String

    data class Advised(
        val flowTemperature: TemperatureC,
        override val source: String,
    ) : FlowTemperatureGuidance

    data class Withheld(override val source: String) : FlowTemperatureGuidance
}

/**
 * The coefficients and the validated envelope of one version of the simplified method.
 *
 * Declared with no members at M1-09 on purpose — naming the accessors would have committed to the
 * shape of a method the ⚑ gate had not validated. M2-01 gives it that shape and still not one
 * number: every accessor returns a [Sourced] value, and the values arrive at the gate.
 *
 * Accessors are keyed by the M1-04 enums rather than by string, so the compiler enumerates exactly
 * the coefficients the gate has to defend, and adding an enum member breaks the build instead of
 * silently returning nothing.
 *
 * **The base temperature is deliberately absent.** M1-04 resolves it at the boundary into
 * `InputsSnapshot` via `DepartementClimate`, so a study stays reproducible when a département's
 * tabulated value is later corrected. An accessor here would be a second path to the same number.
 */
interface FormulaSet {

    /** Where this version of the method holds — and therefore where it must refuse. */
    val envelope: ValidatedEnvelope

    /** Default U-value for a dwelling of this period and insulation level. */
    fun uValueFor(
        period: ConstructionPeriod,
        insulation: InsulationLevel,
    ): Sourced<ThermalTransmittance>

    /** Air renewal implied by this ventilation type. */
    fun airChangeRateFor(ventilation: VentilationType): Sourced<AirChangeRate>

    /** Volumetric heat capacity of air. Physical, but still a number, so still sourced. */
    fun airVolumetricHeatCapacity(): Sourced<VolumetricHeatCapacity>

    /** Heat-losing envelope area per square metre of heated floor — the core simplification. */
    fun envelopeAreaFactor(): Sourced<EnvelopeAreaFactor>

    /** How far below the heat load a machine may be selected. */
    fun underSizingMargin(): Sourced<Percentage>

    /** How far above the heat load a machine may be selected. Independent of the under margin. */
    fun overSizingMargin(): Sourced<Percentage>

    /** Loi d'eau guidance for these emitters, or a sourced refusal to advise. */
    fun flowTemperatureFor(emitter: EmitterType): FlowTemperatureGuidance
}

/**
 * Driven port — supplies the formula set in force on a given date.
 *
 * Date-parameterised for the same reason the rule packs are (`CLAUDE.md` §4.4): a study recomputed
 * years later must apply the method as it stood when the devis was written, not the current one.
 * Without the parameter the core would have to ask "what is the method now", which is a clock.
 */
interface FormulaSetProvider {

    fun formulaSetOn(effectiveDate: EffectiveDate): FormulaSet
}
