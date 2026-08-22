package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidLine
import fr.pacpilot.core.aids.model.AidRule
import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.AidsResolution
import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.aids.port.ResolveAids
import fr.pacpilot.core.aids.port.RulePackRepository
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur

/**
 * Evaluates the barème in force on the devis date, behind [ResolveAids].
 *
 * **Mechanism only — not one barème value lives here.** Every amount, rate and cap arrives from the
 * pack, which is what lets this engine be written and verified while the M6 ⚑ gate on the real
 * MaPrimeRénov'/CEE/TVA figures is still open: closing that gate publishes a pack, and no file in
 * this package changes.
 *
 * The three rule shapes are evaluated separately rather than normalised into one amount, preserving
 * the distinction M1-07 modelled. Collapsing them here would move the difference between "a forfait
 * of 500 €" and "50 % capped at 2 000 €" into conditional logic, and a reviewer holding a published
 * fiche would have nothing to lay it beside.
 *
 * Deterministic by construction: no clock, no randomness, and the effective date is a parameter that
 * selects the pack. Both are enforced by ArchUnit rather than by convention.
 */
class AidsEngine(private val rulePacks: RulePackRepository) : ResolveAids {

    override fun resolve(inputs: AidsInputs, effectiveDate: EffectiveDate): AidsOutcome {
        val pack = rulePacks.packEffectiveOn(effectiveDate)
            ?: return AidsOutcome.NoPackPublished(effectiveDate)

        return AidsOutcome.Resolved(
            AidsResolution(
                aids = ResolvedAids(
                    packVersion = pack.version,
                    lines = pack.payload.aids.mapNotNull { rule -> lineFor(rule, inputs) },
                ),
                workCost = inputs.workCost,
            ),
        )
    }

    /**
     * One aid line, or nothing at all where the rule does not reach these inputs.
     *
     * **`mapNotNull`, not a zero line.** A zero on a devis reads as "this scheme pays you nothing",
     * which is a different statement from "this scheme was not in play" — and the first is a claim
     * about the household that the barème did not make. A tier the pack does not publish is the
     * second case.
     *
     * The `when` is exhaustive with no `else`: a fourth mechanism must break compilation, because
     * adding one is a deliberate modelling act and falling through silently would price a devis
     * without it.
     */
    private fun lineFor(rule: AidRule, inputs: AidsInputs): AidLine? = when (rule) {
        is AidRule.IncomeTiered ->
            rule.amountByDecile[inputs.incomeDecile]?.let { tier -> lineOf(rule, cappedAt(tier, rule.cap)) }

        is AidRule.Forfait -> lineOf(rule, rule.amount)

        is AidRule.RateBased ->
            lineOf(rule, cappedAt(rule.rate.applyTo(inputs.workCost), rule.cap))
    }

    /**
     * The rule's own ceiling, where it declares one.
     *
     * `minOf` over `Comparable`, not a hand-rolled comparison on cents: one ordering for money in
     * the product, and the boundary — an amount landing exactly on its cap — is pinned by test and
     * by golden vector rather than left to a reviewer.
     */
    private fun cappedAt(amount: MoneyEur, cap: MoneyEur?): MoneyEur =
        if (cap == null) amount else minOf(amount, cap)

    /**
     * Carries the rule's identity and its source onto the line, inseparably.
     *
     * [AidLine.rule] is the audit trail: "4 000 EUR" is a number, "rule `sample-forfait` of pack
     * `sample-2025-H1`" is something a reviewer can check against a published fiche. The label is
     * the rule's own, never anything derived from the inputs — an income-tiered line names the
     * scheme and never the tier that matched, which would put a household's income band on a
     * document the client keeps (§4.6, M3-08).
     */
    private fun lineOf(rule: AidRule, amount: MoneyEur): AidLine = AidLine(
        rule = rule.id,
        label = rule.label,
        amount = amount,
        source = rule.source,
    )
}
