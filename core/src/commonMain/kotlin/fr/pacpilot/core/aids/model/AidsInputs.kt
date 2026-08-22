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
        // The rejected value is deliberately not quoted back. An out-of-range integer is arguably
        // not a household's income band at all — but "no message ever renders a decile" is a rule
        // that can be checked by grep and held to, and "no message renders a *valid* decile" is a
        // judgement call at every future call site. The cheaper rule is the one that survives.
        require(value in 1..10) { "an income decile is 1..10" }
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
 * What the aids engine answers: the invoice as the VAT rate leaves it, the itemized aids, and what
 * the client is left to pay.
 *
 * **[appliedVatRate] is a rate on what is charged, not a subsidy paid toward it** (PRODUCT-VIEWS
 * #3, and the reason [VatRate] was deliberately kept out of [AidRule] at M1-07). It therefore
 * *raises* [totalIncludingVat] and the aids come off that, exactly as `Quote.resteACharge` computes
 * against `Quote.totalIncludingVat`. Treating TVA as an aid would subtract it alongside
 * MaPrimeRénov' and produce a reste-à-charge wrong by the whole VAT amount twice over — a figure
 * that still looks entirely plausible to everyone who reads it, which is the worst kind of wrong
 * this product can produce.
 *
 * The applied rate is carried rather than merely used, because the devis and the PDF have to show
 * it: the audit chain is visible on the artefact (PRODUCT-VIEWS #8), and a rate that only existed
 * inside a calculation could not be checked against the pack afterwards.
 *
 * Every figure below is **derived** rather than stored, for the same reason [ResolvedAids.total]
 * is — one source of truth for the number the homeowner reads off the screen on-site.
 */
data class AidsResolution(
    val aids: ResolvedAids,
    /** Hors taxes, as supplied in [AidsInputs.workCost]. */
    val workCost: MoneyEur,
    val appliedVatRate: VatRate,
) {

    val vat: MoneyEur get() = appliedVatRate.rate.applyTo(workCost)

    /**
     * The invoice as this resolution estimates it: the pack's rate applied once to the whole work
     * cost.
     *
     * **Named `estimated` because a devis computes this differently, and the devis wins.**
     * `Quote.totalIncludingVat` folds `LineItem.totalIncludingVat`, rounding VAT *per line* — which
     * is correct, because TVA applies conditionally per line (`CLAUDE.md` §6b, M1-08). Rounding once
     * over the total and rounding per line are not the same arithmetic: three lines of 33,33 € at
     * 10 % round to 3,33 each, totalling 9,99, while the same 99,99 € taxed once rounds to 10,00.
     * `AidsVatSemanticsTest` pins that one-cent gap so it cannot quietly become two.
     *
     * So this is the figure for the pre-devis screen, where an installer has a work-cost estimate
     * and no lines yet. **The moment a `Quote` exists, its total is the authoritative one** and this
     * must not be persisted, rendered, or compared against it — a divergence of one cent between the
     * two would surface at M8 as an anomaly flag in front of a homeowner rather than as a red build.
     *
     * The names carry the distinction so a call site cannot reach for the wrong one by habit. How
     * the quote path supplies its own total is M4-05's to decide, recorded on PAC-51.
     */
    val estimatedTotalIncludingVat: MoneyEur get() = workCost + vat

    /** The reste-à-charge for the pre-devis estimate. See [estimatedTotalIncludingVat]. */
    val estimatedResteACharge: ResteACharge get() = ResteACharge.of(estimatedTotalIncludingVat, aids)
}
