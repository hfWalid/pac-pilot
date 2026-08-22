package fr.pacpilot.core.quoting.model

import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.aids.model.ResteACharge
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.PowerKw
import fr.pacpilot.core.shared.ProductId
import fr.pacpilot.core.shared.QuoteId

/**
 * The machine as it was when the devis was written.
 *
 * A copy, not a pointer. `Product` is Catalog-owned and lives server-side (DELIVERY-PLAN §3), and
 * catalogues change: a model is discontinued, a price is revised, a specification is corrected. Any
 * of those would silently rewrite a devis issued last year if this held only a [ProductId] — the
 * document in the client's file would stop matching the system's account of it.
 *
 * Only the attributes that must survive: what was sold, the figure it was selected on, and what it
 * cost that day. Everything else is a catalogue lookup that is allowed to move.
 */
data class ProductSnapshot(
    val id: ProductId,
    val model: String,
    /** The selection criterion — catalogue power at −7 °C (`CLAUDE.md` §3). */
    val powerAtMinusSevenC: PowerKw,
    val priceAtQuoteTime: MoneyEur,
) {
    init {
        require(model.isNotBlank()) { "a product snapshot records which model was sold" }
    }
}

/**
 * One priced line of the devis.
 *
 * The VAT rate sits on the line, not on the quote, because TVA at 5,5 % applies conditionally
 * (`CLAUDE.md` §6b) — the machine and its installation can qualify while an unrelated item on the
 * same devis does not. *Which* rate applies is the M3 evaluator's decision; the line only records
 * the one that was applied.
 *
 * Every total is derived. A stored line total is a second source of truth that drifts from its
 * inputs the first time one is corrected.
 */
data class LineItem(
    val label: String,
    val unitPrice: MoneyEur,
    val quantity: Int,
    val vatRate: Percentage,
) {

    init {
        require(label.isNotBlank()) { "a line item must describe what is being charged" }
        require(quantity > 0) { "a line item's quantity is at least one, was $quantity" }
    }

    /** Hors taxes. */
    val total: MoneyEur get() = unitPrice * quantity

    val vat: MoneyEur get() = vatRate.applyTo(total)

    /** Toutes taxes comprises. */
    val totalIncludingVat: MoneyEur get() = total + vat
}

/**
 * Where a devis stands with the client — the state machine of `ARCHITECTURE` #7.
 *
 * The transitions are constrained rather than advisory. A free enum lets a rejected devis be
 * marked accepted, which is not a state a devis can reach and not something a later reader could
 * distinguish from a genuine acceptance.
 *
 * `ACCEPTED` and `REJECTED` are terminal. Changing a client's mind means a new devis, which is
 * also how the client's file reads.
 */
enum class QuoteStatus {
    DRAFT,
    QUOTED,
    AIDS_RESOLVED,
    SENT,
    ACCEPTED,
    REJECTED,
    ;

    val allowedNext: Set<QuoteStatus>
        get() = when (this) {
            DRAFT -> setOf(QUOTED)
            QUOTED -> setOf(AIDS_RESOLVED)
            AIDS_RESOLVED -> setOf(SENT)
            SENT -> setOf(ACCEPTED, REJECTED)
            ACCEPTED, REJECTED -> emptySet()
        }

    fun canTransitionTo(next: QuoteStatus): Boolean = next in allowedNext
}

/**
 * The devis: what is sold, what it costs, which aids applied, and what the client is left to pay
 * (`CLAUDE.md` §9).
 *
 * Core-owned aggregate (DELIVERY-PLAN §3), in `commonMain` because the installer's device builds it
 * offline and the server recomputes it on sync.
 *
 * **Holds a [ValidatedDimensioning], not an id.** `ARCHITECTURE` #7 allows only `Validated →
 * Quoted`, and taking the validated type makes that a compile-time fact rather than a check in a
 * service somebody can forget. It also satisfies reproducibility directly: from a quote you can
 * reach the inputs snapshot and the rule-pack version, which is everything needed to recompute the
 * document from scratch years later.
 *
 * **Not a `data class`.** `copy()` would let the status be replaced without passing through
 * [transitionTo], which is the entire point of constraining the transitions. Identity equality
 * instead — an aggregate is the same aggregate when it has the same id.
 *
 * Every total is derived, so a quote whose total contradicts its lines is not constructible.
 */
class Quote internal constructor(
    val id: QuoteId,
    val dimensioning: ValidatedDimensioning,
    val product: ProductSnapshot,
    val lines: List<LineItem>,
    val resolvedAids: ResolvedAids,
    /** The devis date. Resolved [resolvedAids]'s pack; see the note on reproducibility below. */
    val effectiveDate: EffectiveDate,
    val status: QuoteStatus,
) {

    init {
        require(lines.isNotEmpty()) { "a devis with no lines prices nothing" }
    }

    /** Work cost hors taxes. */
    val subtotalExcludingVat: MoneyEur
        get() = lines.fold(MoneyEur.ZERO) { running, line -> running + line.total }

    val vat: MoneyEur
        get() = lines.fold(MoneyEur.ZERO) { running, line -> running + line.vat }

    /** What the job costs before aids. */
    val totalIncludingVat: MoneyEur
        get() = lines.fold(MoneyEur.ZERO) { running, line -> running + line.totalIncludingVat }

    /**
     * What the client actually pays — the figure shown live on-site.
     *
     * Computed against the TTC total, because the aids are paid toward what the client is invoiced,
     * and TVA is part of that rather than a subsidy against it.
     */
    val resteACharge: ResteACharge
        get() = ResteACharge.of(totalIncludingVat, resolvedAids)

    /**
     * Moves the devis to its next state, refusing any move `ARCHITECTURE` #7 does not allow.
     *
     * Returns a new instance; nothing mutates. The refused moves that matter are the ones that
     * would rewrite history — a rejected devis becoming accepted, or a draft appearing to have been
     * sent without ever having had its aids resolved.
     */
    fun transitionTo(next: QuoteStatus): Quote {
        require(status.canTransitionTo(next)) {
            "a devis does not move from $status to $next; allowed: ${status.allowedNext}"
        }
        return Quote(id, dimensioning, product, lines, resolvedAids, effectiveDate, next)
    }

    override fun equals(other: Any?): Boolean = other is Quote && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Quote(" + id.value + ", " + status.name + ")"

    companion object {
        /** A devis is born a draft. Every other state is reached through [transitionTo]. */
        fun draft(
            id: QuoteId,
            dimensioning: ValidatedDimensioning,
            product: ProductSnapshot,
            lines: List<LineItem>,
            resolvedAids: ResolvedAids,
            effectiveDate: EffectiveDate,
        ): Quote = Quote(
            id = id,
            dimensioning = dimensioning,
            product = product,
            lines = lines,
            resolvedAids = resolvedAids,
            effectiveDate = effectiveDate,
            status = QuoteStatus.DRAFT,
        )
    }
}
