package fr.pacpilot.core.quoting.port

import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.dimensioning.model.Dimensioning
import fr.pacpilot.core.quoting.model.LineItem
import fr.pacpilot.core.quoting.model.Quote
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.ProductId

/**
 * Driving port — assemble the devis (`ARCHITECTURE` #5).
 *
 * Takes the whole [Dimensioning] rather than its id so the precondition is expressible in the
 * contract: a devis is built from a study a professional has **validated** (`CLAUDE.md` §4.5), and
 * only the aggregate knows whether it has been. Passing an id would push that check into the
 * implementation where a caller cannot see it.
 *
 * This is the one place Quoting depends on Dimensioning. The direction is one-way and stays that
 * way: Dimensioning has no idea a devis exists.
 *
 * Implemented at M4, when the flow is wired end to end.
 */
interface BuildQuote {

    fun build(
        dimensioning: Dimensioning,
        selectedProduct: ProductId,
        lines: List<LineItem>,
        resolvedAids: ResolvedAids,
        effectiveDate: EffectiveDate,
    ): Quote
}
