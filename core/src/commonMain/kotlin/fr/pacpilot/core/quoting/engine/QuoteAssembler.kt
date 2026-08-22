package fr.pacpilot.core.quoting.engine

import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning
import fr.pacpilot.core.quoting.model.LineItem
import fr.pacpilot.core.quoting.model.ProductSnapshot
import fr.pacpilot.core.quoting.model.Quote
import fr.pacpilot.core.quoting.port.BuildQuote
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.QuoteId

/**
 * Assembles the devis, behind [BuildQuote].
 *
 * **Thin on purpose, and not empty.** Most of what makes a devis correct is already enforced by the
 * types: [Quote.draft] takes a [ValidatedDimensioning] so an unsigned study cannot be quoted, refuses
 * an empty line list, and derives every total. What is left is the one rule that spans the pieces and
 * belongs to no single one of them — the consistency check below.
 *
 * It exists as a class rather than as a call to [Quote.draft] from an adapter because the driving
 * port is what the SDK ambition of `CLAUDE.md` §3 rests on: a grossiste portal embedding the engines
 * gets a use case, not a companion-object factory it has to assemble arguments for correctly.
 *
 * The id is supplied by a function rather than a parameter because a devis is created offline on the
 * device (`CLAUDE.md` §4.3) and the caller owns identity — but the assembler still must not reach for
 * a generator of its own, which would be a hidden source of non-determinism in the core.
 */
class QuoteAssembler(private val nextId: () -> QuoteId) : BuildQuote {

    override fun build(
        dimensioning: ValidatedDimensioning,
        selectedProduct: ProductSnapshot,
        lines: List<LineItem>,
        resolvedAids: ResolvedAids,
        effectiveDate: EffectiveDate,
    ): Quote {
        // A devis dated before the study it quotes is not a devis anyone can defend: the barème it
        // applied would be older than the method that produced the figures it prices. Neither type
        // can see the other's date, so the check belongs here.
        require(dimensioning.effectiveDate <= effectiveDate) {
            "a devis cannot predate the study it is built on: study " +
                dimensioning.effectiveDate.render() + ", devis " + effectiveDate.render()
        }

        return Quote.draft(
            id = nextId(),
            dimensioning = dimensioning,
            product = selectedProduct,
            lines = lines,
            resolvedAids = resolvedAids,
            effectiveDate = effectiveDate,
        )
    }
}
