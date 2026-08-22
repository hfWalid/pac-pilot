package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidRule
import fr.pacpilot.core.aids.model.AidRuleId
import fr.pacpilot.core.aids.model.AidRulePack
import fr.pacpilot.core.aids.model.AidRulePackPayload
import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.VatRate
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage

private const val TBD = "SOURCE_TBD"

private const val WHY_PROVISIONAL =
    " (sample pack; real bareme encoded at M6 behind the human gate)"

/**
 * Two adjoining barème versions with no validated content whatsoever, so M3 can be built and
 * verified before the M6 ⚑ gate.
 *
 * ## Why this lives in `commonTest`
 *
 * The same decision M2-07 made for `ProvisionalFormulaSet`, for the same reason and with a sharper
 * edge here. A sample pack is exactly the artefact that quietly becomes authoritative: someone
 * wires it into a demo, the demo becomes a pilot, and a placeholder MaPrimeRénov' figure is shown
 * to a homeowner as what they will actually receive. `CLAUDE.md` §12 names an unsourced number that
 * looks authoritative as the failure mode this project cannot afford, and a euro amount on a devis
 * is the most quotable number the product produces.
 *
 * `commonTest` makes shipping it **impossible by construction** rather than discouraged by a guard:
 * it is not on the production classpath of either target, so no naming convention or build check is
 * needed to enforce what the module boundary already enforces.
 *
 * ## Why the numbers look wrong
 *
 * Deliberately, and in the same spirit as `ProvisionalFormulaSet`'s non-physical coefficients.
 *
 * - **The income-tiered aid pays *more* to higher deciles.** MaPrimeRénov' does the opposite — it
 *   is means-tested downward, most to the lowest incomes. Anyone reading this table against the
 *   real barème sees it is inverted in the first two rows.
 * - **The rate-based aid is 50 %**, which no French scheme pays.
 * - **The VAT rate is 10 %**, which is neither the 5,5 % reduced rate for this work nor the 20 %
 *   standard rate. It cannot be mistaken for either.
 * - Every amount is a round thousand or hundred. Real barèmes are not.
 *
 * Their only job is to exercise the evaluator and let the golden vectors bind the two targets
 * together. Every rule carries [TBD] with a reason, so [AidRulePack.isProvisional] is true and
 * stays true.
 */
object SampleAidRulePacks {

    /** Ids are stable across both versions, so a handover changes amounts and not identity. */
    val INCOME_TIERED: AidRuleId = AidRuleId("sample-income-tiered")
    val FORFAIT: AidRuleId = AidRuleId("sample-forfait")
    val RATE_BASED: AidRuleId = AidRuleId("sample-rate-based")

    /**
     * The earlier version. Closed range — `effectiveTo` is **inclusive** (M1-07), so this pack
     * prices a devis dated exactly 2025-06-30 and [SECOND_HALF] takes over on 2025-07-01.
     */
    val FIRST_HALF: AidRulePack = AidRulePack(
        version = AidRulePackVersion("sample-2025-H1"),
        effectiveFrom = EffectiveDate(2025, 1, 1),
        effectiveTo = EffectiveDate(2025, 6, 30),
        payload = AidRulePackPayload(
            vatRate = VatRate(Percentage.ofWholePercent(10), TBD + WHY_PROVISIONAL),
            aids = listOf(
                incomeTiered(baseEuros = 1_000),
                forfait(euros = 500),
                rateBased(percent = 50, capEuros = 2_000),
            ),
        ),
        checksum = "sample-checksum-h1",
        signature = "sample-signature-h1",
    )

    /**
     * The successor, open-ended. Every amount differs from [FIRST_HALF] so a resolution that
     * silently picked "the latest pack" would produce visibly different figures rather than
     * coincidentally identical ones — which is what makes the M3-06 handover assertions bite.
     */
    val SECOND_HALF: AidRulePack = AidRulePack(
        version = AidRulePackVersion("sample-2025-H2"),
        effectiveFrom = EffectiveDate(2025, 7, 1),
        effectiveTo = null,
        payload = AidRulePackPayload(
            vatRate = VatRate(Percentage.ofWholePercent(20), TBD + WHY_PROVISIONAL),
            aids = listOf(
                incomeTiered(baseEuros = 2_000),
                forfait(euros = 800),
                rateBased(percent = 25, capEuros = 3_000),
            ),
        ),
        checksum = "sample-checksum-h2",
        signature = "sample-signature-h2",
    )

    val BOTH: List<AidRulePack> = listOf(FIRST_HALF, SECOND_HALF)

    /**
     * Deciles 1..9 only, and rising with income.
     *
     * **Decile 10 is deliberately absent**, and that absence is load-bearing: it is the fixture for
     * "this scheme is not in play for this household", which the evaluator must express as *no
     * line at all* rather than a zero line (M3-03). A real means-tested scheme excludes its top
     * deciles, so the shape is right even though the direction is inverted.
     */
    private fun incomeTiered(baseEuros: Long): AidRule.IncomeTiered = AidRule.IncomeTiered(
        id = INCOME_TIERED,
        label = "Aide indexee sur le decile (echantillon)",
        source = TBD + WHY_PROVISIONAL,
        amountByDecile = (1..9).associate { decile ->
            IncomeDecile(decile) to MoneyEur.ofEuros(baseEuros * decile)
        },
        cap = null,
    )

    private fun forfait(euros: Long): AidRule.Forfait = AidRule.Forfait(
        id = FORFAIT,
        label = "Forfait fixe (echantillon)",
        source = TBD + WHY_PROVISIONAL,
        amount = MoneyEur.ofEuros(euros),
    )

    private fun rateBased(percent: Int, capEuros: Long): AidRule.RateBased = AidRule.RateBased(
        id = RATE_BASED,
        label = "Aide au taux plafonne (echantillon)",
        source = TBD + WHY_PROVISIONAL,
        rate = Percentage.ofWholePercent(percent),
        cap = MoneyEur.ofEuros(capEuros),
    )
}
