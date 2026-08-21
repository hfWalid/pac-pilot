package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur

/**
 * One aid, itemized, with the authority it came from.
 *
 * Itemization is not presentation. A homeowner comparing two devis needs to see MaPrimeRénov' and
 * the CEE separately, and an artisan defending a figure needs to name the fiche it came from —
 * `BAR-TH-171` is an answer, "aides: 5 400 €" is not.
 */
data class AidLine(val label: String, val amount: MoneyEur, val source: String) {

    init {
        require(label.isNotBlank()) { "an aid line must name the aid" }
        require(source.isNotBlank()) { "an aid line must cite the fiche or barème it comes from" }
        require(amount >= MoneyEur.ZERO) {
            "an aid does not take money away, was " + amount.render() + "; model a deduction as a quote line"
        }
    }
}

/**
 * The aids that applied to one devis, tied to the exact pack version that produced them.
 *
 * [packVersion] is the field that makes a devis reproducible (`CLAUDE.md` §7): the stored numbers
 * are a convenience, the pack version is the proof. Recomputing from the pack must reproduce these
 * lines exactly, which is what the server's verification pass asserts on sync (§4.2).
 *
 * [total] is **derived, never stored**. A stored total is a second source of truth that drifts from
 * its lines the first time one is corrected, and it is the number a client reads off the screen.
 */
data class ResolvedAids(
    val packVersion: AidRulePackVersion,
    val lines: List<AidLine>,
) {

    val total: MoneyEur
        get() = lines.fold(MoneyEur.ZERO) { running, line -> running + line.amount }

    companion object {
        fun none(packVersion: AidRulePackVersion): ResolvedAids = ResolvedAids(packVersion, emptyList())
    }
}

/**
 * What the homeowner actually pays — the number the whole on-site pitch turns on
 * (`PRODUCT-VIEWS.md`: the high-value moment is showing the reste-à-charge live).
 *
 * **Not clamped at zero on purpose.** Aids exceeding the work cost means a barème was misapplied or
 * a pack is wrong, and silently flooring the figure at `0.00` would hide that behind a number that
 * looks plausible. This product's standing rule for a computation that disagrees with itself is to
 * persist and flag, never to correct quietly (§4.2) — [isOverGranted] is that flag.
 *
 * `TODO(unverified)`: whether a real barème caps the aid at the work cost, or whether the excess is
 * simply lost, is an M3 question with a citation. Until then the model refuses to assume either.
 */
data class ResteACharge(val amount: MoneyEur) {

    val isOverGranted: Boolean get() = amount < MoneyEur.ZERO

    companion object {
        fun of(workCost: MoneyEur, aids: ResolvedAids): ResteACharge =
            ResteACharge(workCost - aids.total)
    }
}
