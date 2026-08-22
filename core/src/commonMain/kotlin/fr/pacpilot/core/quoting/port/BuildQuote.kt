package fr.pacpilot.core.quoting.port

import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning
import fr.pacpilot.core.quoting.model.LineItem
import fr.pacpilot.core.quoting.model.ProductSnapshot
import fr.pacpilot.core.quoting.model.Quote
import fr.pacpilot.core.shared.EffectiveDate

/**
 * Driving port — assemble the devis (`ARCHITECTURE` #5).
 *
 * Takes a [ValidatedDimensioning] rather than an id or the sealed supertype, so the precondition is
 * a compile-time fact: a devis is built from a study a professional has **signed** (`CLAUDE.md`
 * §4.5, `ARCHITECTURE` #7's `Validated → Quoted`). An id would push that check into the
 * implementation where no caller can see it; the supertype would push it to a runtime error.
 *
 * This is the one place Quoting depends on Dimensioning. The direction is one-way and stays that
 * way: Dimensioning has no idea a devis exists.
 *
 * Implemented at M4, when the flow is wired end to end.
 */
interface BuildQuote {

    fun build(
        dimensioning: ValidatedDimensioning,
        selectedProduct: ProductSnapshot,
        lines: List<LineItem>,
        resolvedAids: ResolvedAids,
        effectiveDate: EffectiveDate,
    ): Quote
}
