package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.MoneyEur

/**
 * The household's fiscal income decile, which sets the MaPrimeRénov' tier (`CLAUDE.md` §6b).
 *
 * **Sensitive personal data** (§4.6). It is a domain input because the aid cannot be computed
 * without it, but nothing in the core logs, renders or transmits it — RGPD handling, retention and
 * the DPA obligations are adapter and operations concerns at M4 and M11.
 *
 * Deciles rather than the aid scheme's colour tiers because §4.6 names deciles as what is stored.
 * The mapping from decile to tier is a barème rule and belongs to the M3 rule pack, not here.
 */
data class IncomeDecile(val value: Int) {
    init {
        require(value in 1..10) { "an income decile is 1..10, was $value" }
    }

    /**
     * Redacted on purpose, overriding what `data class` would generate.
     *
     * §4.6 treats fiscal income as sensitive. The generated `toString` would print the decile, and
     * every enclosing data class renders its fields through it — so one log line of an `AidsInputs`
     * or a dossier would put a household's income band into a log file that outlives the request
     * and was never scoped to hold it. Read [value] deliberately, or do not read it.
     */
    override fun toString(): String = "IncomeDecile(redacted)"
}

/**
 * The kind of heat pump being installed.
 *
 * `TODO(unverified)`: confirm at M3 against the CEE fiches — BAR-TH-171 covers air-eau and the
 * aid depends on the fiche that applies. Géothermie is deliberately absent: §3 puts it out of scope
 * for V1, so a member for it would be scope the product does not have.
 */
enum class HeatPumpType {
    AIR_WATER,
    AIR_AIR,
}

/**
 * What the new heat pump replaces, which several aids are conditioned on.
 *
 * `TODO(unverified)`: the CEE bonification for replacing a fossil-fuel boiler is real, but which
 * systems qualify and at what rate is a barème fact for M3 with a fiche cited. These members are the
 * distinctions an installer can observe on-site, not a claim about which ones earn money.
 */
enum class ReplacedSystem {
    OIL_BOILER,
    GAS_BOILER,
    ELECTRIC_HEATING,
    OTHER,
}

/**
 * Everything the aids engine needs, resolved at the boundary and passed inward (`CLAUDE.md` §6b).
 *
 * The effective date is deliberately **not** a field here. It is a parameter on the port instead,
 * so the no-hidden-clock rule stays structural: an engine cannot resolve a barème without a caller
 * having stated which date's barème it wants (§10).
 */
data class AidsInputs(
    val incomeDecile: IncomeDecile,
    val heatPumpType: HeatPumpType,
    val climateZone: ClimateZone,
    val replacedSystem: ReplacedSystem,
    val workCost: MoneyEur,
) {
    init {
        require(workCost > MoneyEur.ZERO) {
            "aids are computed against a positive work cost, was " + workCost.render()
        }
    }
}

/**
 * What the aids engine answers: the itemized aids, and what the client is left to pay.
 *
 * [resteACharge] is **derived** rather than stored, for the same reason [ResolvedAids.total] is —
 * one source of truth for the figure the homeowner reads off the screen on-site.
 */
data class AidsResolution(val aids: ResolvedAids, val workCost: MoneyEur) {
    val resteACharge: ResteACharge get() = ResteACharge.of(workCost, aids)
}
