package fr.pacpilot.core.quoting.model

import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.aids.model.ResteACharge
import fr.pacpilot.core.shared.DimensioningId
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.ProductId
import fr.pacpilot.core.shared.QuoteId

/** One priced line of the devis. [total] is derived; a stored line total is a second truth. */
data class LineItem(val label: String, val unitPrice: MoneyEur, val quantity: Int) {

    init {
        require(label.isNotBlank()) { "a line item must describe what is being charged" }
        require(quantity > 0) { "a line item's quantity is at least one, was $quantity" }
    }

    val total: MoneyEur get() = unitPrice * quantity
}

/**
 * Where a devis stands with the client.
 *
 * `TODO(unverified)`: these four are the states V1 can observe without the electronic signature
 * deferred to V2 (`CLAUDE.md` §3). Treated as an **open** enum in the same sense as `Intervention`'s
 * statuses in §14 — V2's signature path should be an addition, not a migration of meaning, so
 * nothing here should be given a meaning that a signed state would have to overload.
 */
enum class QuoteStatus {
    DRAFT,
    ISSUED,
    ACCEPTED,
    DECLINED,
}

/**
 * The devis: what is being sold, what it costs, which aids applied, and what the client is left to
 * pay (`CLAUDE.md` §9).
 *
 * Core-owned aggregate (DELIVERY-PLAN §3), in `commonMain` because the installer's device builds it
 * offline and the server recomputes it on sync.
 *
 * [effectiveDate] is the devis date, and it is what resolved [resolvedAids]'s pack. The two are
 * stored together so the pairing survives; **the model cannot verify that pairing itself** — that
 * needs the pack, which the M3 engine resolves. Until then this is an invariant the engine owns and
 * this aggregate merely records honestly rather than pretending to enforce.
 *
 * [subtotal] and [resteACharge] are derived for the same reason [ResolvedAids.total] is.
 */
data class Quote(
    val id: QuoteId,
    val dimensioningId: DimensioningId,
    val selectedProduct: ProductId,
    val lines: List<LineItem>,
    val resolvedAids: ResolvedAids,
    val effectiveDate: EffectiveDate,
    val status: QuoteStatus,
) {

    init {
        require(lines.isNotEmpty()) { "a devis with no lines prices nothing" }
    }

    /** Total work cost before aids. */
    val subtotal: MoneyEur
        get() = lines.fold(MoneyEur.ZERO) { running, line -> running + line.total }

    /** What the client pays after aids — the figure shown live on-site. */
    val resteACharge: ResteACharge
        get() = ResteACharge.of(subtotal, resolvedAids)
}
