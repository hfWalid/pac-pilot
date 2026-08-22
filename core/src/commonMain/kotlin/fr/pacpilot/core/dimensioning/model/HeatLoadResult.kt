package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.PowerBand
import fr.pacpilot.core.shared.PowerKw
import fr.pacpilot.core.shared.TemperatureC

/**
 * How much weight the installer may put on a result when signing it.
 *
 * Derived, never supplied. Today confidence is entirely a function of one thing: whether every
 * coefficient behind the result cites a source. That is the honest answer while the M2 ⚑ gate is
 * open, and deriving it means it cannot drift from the assumptions it describes.
 *
 * `TODO(unverified)`: M2 may find a second axis — inputs sitting near the edge of the validated
 * envelope without falling outside it. If so, confidence becomes something the engine supplies
 * rather than something the result computes, and this enum grows a member with a citation.
 */
enum class Confidence {
    /** At least one coefficient is unsourced. Usable as guidance, not as an authoritative figure. */
    INDICATIVE,

    /** Every assumption cites a source. */
    SUPPORTED,
}

/**
 * A computed heat load with its reasoning attached (`CLAUDE.md` §6a, §4.5).
 *
 * A **proposal**, not a decision, and deliberately not a bare number: PRODUCT-VIEWS #5 requires the
 * installer to see what the calculation assumed *before* validating it, because you cannot
 * meaningfully sign off on a figure whose basis you cannot inspect. That is also why validation
 * lives on the [Dimensioning] aggregate as a separate [ValidationAct] rather than as a flag here.
 *
 * [assumptions] is required non-empty. A result with no recorded reasoning is a bug rather than an
 * edge case — it would mean the engine applied defaults it did not disclose.
 *
 * [recommendedFlowTemperature] is nullable: loi d'eau guidance depends on emitter type, and until
 * the M2 gate settles which emitter implies which flow temperature, a method that cannot say must
 * say nothing rather than guess.
 */
data class HeatLoadResult(
    val heatLoad: PowerKw,
    val recommendedPowerBand: PowerBand,
    val recommendedFlowTemperature: TemperatureC?,
    val assumptions: AssumptionsLog,
) {

    init {
        require(assumptions.entries.isNotEmpty()) {
            "a computed result records what it assumed; an empty assumptions log is a bug, not an edge case"
        }
    }

    val confidence: Confidence
        get() = if (assumptions.isProvisional) Confidence.INDICATIVE else Confidence.SUPPORTED

    /** True while any coefficient behind this result is unsourced. See [AssumptionsLog]. */
    val isProvisional: Boolean get() = assumptions.isProvisional
}
