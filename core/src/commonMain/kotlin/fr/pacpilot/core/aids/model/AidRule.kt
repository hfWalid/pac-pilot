package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage

/** Stable handle for one rule inside a pack. A resolved aid line points back at it. */
data class AidRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "AidRuleId must not be blank" }
        require(value == value.trim()) { "AidRuleId must not carry surrounding whitespace" }
    }

    override fun toString(): String = value
}

/**
 * One aid mechanism, as a barème expresses it.
 *
 * Three shapes because the schemes genuinely work three different ways, and forcing them into one
 * would fit none of them: MaPrimeRénov' pays by income tier, a CEE fiche pays a forfait, and some
 * aids are a rate on the work cost. Flattening these into a single `amount` field would push the
 * distinction into the evaluator, where it becomes conditional logic nobody can audit against a
 * published barème.
 *
 * Equally, this is **not a rules language.** A general expression tree would be a speculative
 * abstraction for three known mechanisms; adding a fourth is adding a member here, and that is a
 * deliberate, reviewable act rather than a data-entry change.
 *
 * **No barème values live in this file.** The pack payload carries them, and encoding the first
 * real ones is M6 behind a ⚑ human gate against anah.gouv.fr / ATEE / DGEC (`CLAUDE.md` §12).
 */
sealed interface AidRule {

    val id: AidRuleId
    val label: String

    /** A citation, or `SOURCE_TBD` while unvalidated. Never blank. */
    val source: String

    val isProvisional: Boolean get() = source.startsWith(SOURCE_TBD)

    /**
     * MaPrimeRénov'-shaped: the amount depends on the household's income tier.
     *
     * The table is explicit rather than a formula because the published barème is a table, and a
     * reviewer has to be able to lay the two side by side.
     */
    data class IncomeTiered(
        override val id: AidRuleId,
        override val label: String,
        override val source: String,
        val amountByDecile: Map<IncomeDecile, MoneyEur>,
        /** Upper limit on what this rule can pay, when the scheme sets one. */
        val cap: MoneyEur?,
    ) : AidRule {
        init {
            requireRule(label, source)
            require(amountByDecile.isNotEmpty()) { "an income-tiered rule needs at least one tier" }
        }
    }

    /** CEE-shaped: a fixed amount, per fiche (BAR-TH-171, BAR-TH-104). */
    data class Forfait(
        override val id: AidRuleId,
        override val label: String,
        override val source: String,
        val amount: MoneyEur,
    ) : AidRule {
        init {
            requireRule(label, source)
            require(amount >= MoneyEur.ZERO) { "a forfait does not take money away" }
        }
    }

    /** A proportion of the work cost, optionally capped. */
    data class RateBased(
        override val id: AidRuleId,
        override val label: String,
        override val source: String,
        val rate: Percentage,
        val cap: MoneyEur?,
    ) : AidRule {
        init { requireRule(label, source) }
    }

    companion object {
        const val SOURCE_TBD: String = "SOURCE_TBD"

        private fun requireRule(label: String, source: String) {
            require(label.isNotBlank()) { "an aid rule must name the aid" }
            require(source.isNotBlank()) {
                "an aid rule cites its barème or says plainly it has none ($SOURCE_TBD)"
            }
        }
    }
}

/**
 * The reduced VAT rate that applies to the invoice.
 *
 * **Not an [AidRule], and that distinction is load-bearing.** TVA at 5,5 % is a reduced rate on
 * what is charged, not a subsidy paid toward it (PRODUCT-VIEWS #3). Modelling it as an aid would
 * subtract it from the work cost alongside MaPrimeRénov' and produce a reste-à-charge that is wrong
 * by the whole VAT amount — a plausible-looking figure, which is the worst kind of wrong here.
 */
data class VatRate(val rate: Percentage, val source: String) {
    init {
        require(source.isNotBlank()) {
            "a VAT rate cites its source or says plainly it has none (${AidRule.SOURCE_TBD})"
        }
    }

    val isProvisional: Boolean get() = source.startsWith(AidRule.SOURCE_TBD)
}

/**
 * What a pack actually contains: the VAT rate in force, and the aid rules to evaluate.
 *
 * Declarative and specific to the three mechanisms above. The evaluator is M3's; this is the data
 * it reads.
 */
data class AidRulePackPayload(val vatRate: VatRate, val aids: List<AidRule>) {

    init {
        val duplicates = aids.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate aid rule ids in one pack: $duplicates" }
    }

    /** True while any rule or the VAT rate is still unsourced. */
    val isProvisional: Boolean get() = vatRate.isProvisional || aids.any { it.isProvisional }

    fun rule(id: AidRuleId): AidRule? = aids.firstOrNull { it.id == id }
}
