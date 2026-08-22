package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AidRulePackTest {

    private val payload = AidRulePackPayload(
        vatRate = VatRate(Percentage(550), AidRule.SOURCE_TBD + " (rate encoded at M6)"),
        aids = listOf(
            AidRule.IncomeTiered(
                id = AidRuleId("mpr"),
                label = "MaPrimeRenov",
                source = AidRule.SOURCE_TBD + " (anah.gouv.fr, encoded at M6)",
                amountByDecile = mapOf(IncomeDecile(1) to MoneyEur.ofEuros(5_000)),
                cap = MoneyEur.ofEuros(5_000),
            ),
            AidRule.Forfait(
                id = AidRuleId("cee-bar-th-171"),
                label = "CEE BAR-TH-171",
                source = AidRule.SOURCE_TBD + " (fiche encoded at M6)",
                amount = MoneyEur.ofEuros(1_400),
            ),
        ),
    )

    private val closedPack = AidRulePack(
        version = AidRulePackVersion("2026-H1"),
        effectiveFrom = EffectiveDate(2026, 1, 1),
        effectiveTo = EffectiveDate(2026, 6, 30),
        payload = payload,
        checksum = "sha256:abc",
        signature = "sig:abc",
    )

    @Test
    fun `covers both boundary days inclusively`() {
        assertTrue(closedPack.covers(EffectiveDate(2026, 1, 1)))
        assertTrue(closedPack.covers(EffectiveDate(2026, 6, 30)))
    }

    @Test
    fun `does not cover the days outside its range`() {
        assertTrue(!closedPack.covers(EffectiveDate(2025, 12, 31)))
        assertTrue(!closedPack.covers(EffectiveDate(2026, 7, 1)))
    }

    @Test
    fun `a successor starting the following day leaves no gap and no overlap`() {
        val successor = closedPack.copy(
            version = AidRulePackVersion("2026-H2"),
            effectiveFrom = EffectiveDate(2026, 7, 1),
            effectiveTo = null,
        )
        val handoverDay = EffectiveDate(2026, 7, 1)
        assertTrue(!closedPack.covers(handoverDay))
        assertTrue(successor.covers(handoverDay))
        assertTrue(closedPack.covers(EffectiveDate(2026, 6, 30)))
        assertTrue(!successor.covers(EffectiveDate(2026, 6, 30)))
    }

    @Test
    fun `refuses a range that runs backward`() {
        assertFailsWith<IllegalArgumentException> {
            closedPack.copy(effectiveFrom = EffectiveDate(2026, 7, 1), effectiveTo = EffectiveDate(2026, 1, 1))
        }
    }

    @Test
    fun `carries all six fields of the pack spec`() {
        // CLAUDE.md 7: version, effective_from, effective_to, payload, checksum, signature.
        assertEquals(AidRulePackVersion("2026-H1"), closedPack.version)
        assertEquals(EffectiveDate(2026, 1, 1), closedPack.effectiveFrom)
        assertEquals(EffectiveDate(2026, 6, 30), closedPack.effectiveTo)
        assertEquals(payload, closedPack.payload)
        assertEquals("sha256:abc", closedPack.checksum)
        assertEquals("sig:abc", closedPack.signature)
    }

    @Test
    fun `refuses a pack published without a checksum or a signature`() {
        assertFailsWith<IllegalArgumentException> { closedPack.copy(checksum = "  ") }
        assertFailsWith<IllegalArgumentException> { closedPack.copy(signature = "  ") }
    }

    @Test
    fun `the payload keeps the three aid mechanisms distinct`() {
        // Flattening these into one shape would push the distinction into the evaluator as
        // conditional logic nobody could audit against a published bareme.
        val tiered = closedPack.payload.rule(AidRuleId("mpr"))
        val forfait = closedPack.payload.rule(AidRuleId("cee-bar-th-171"))
        assertTrue(tiered is AidRule.IncomeTiered)
        assertTrue(forfait is AidRule.Forfait)
        assertNull(closedPack.payload.rule(AidRuleId("nope")))
    }

    @Test
    fun `TVA is a rate on the payload, not one of the aids`() {
        // PRODUCT-VIEWS 3: a reduced rate on the invoice is not a subsidy. Modelling it as an aid
        // would subtract it from the work cost and produce a reste-a-charge wrong by the whole VAT.
        assertEquals(Percentage(550), closedPack.payload.vatRate.rate)
        assertTrue(closedPack.payload.aids.none { it.label.contains("TVA") })
    }

    @Test
    fun `a pack whose rules are unsourced reports itself provisional`() {
        assertTrue(closedPack.isProvisional)

        val sourced = closedPack.copy(
            payload = payload.copy(
                vatRate = VatRate(Percentage(550), "CGI art. 278-0 bis"),
                aids = listOf(
                    AidRule.Forfait(
                        id = AidRuleId("cee-bar-th-171"),
                        label = "CEE BAR-TH-171",
                        source = "fiche BAR-TH-171",
                        amount = MoneyEur.ofEuros(1_400),
                    ),
                ),
            ),
        )
        assertTrue(!sourced.isProvisional)
    }

    @Test
    fun `refuses duplicate rule ids in one pack`() {
        val duplicated = AidRule.Forfait(AidRuleId("x"), "A", "src", MoneyEur.ofEuros(1))
        assertFailsWith<IllegalArgumentException> {
            AidRulePackPayload(payload.vatRate, listOf(duplicated, duplicated.copy(label = "B")))
        }
    }

    @Test
    fun `an aid rule cites its bareme or says plainly it has none`() {
        assertFailsWith<IllegalArgumentException> {
            AidRule.Forfait(AidRuleId("x"), "CEE", "", MoneyEur.ofEuros(1_400))
        }
        assertFailsWith<IllegalArgumentException> {
            AidRule.IncomeTiered(AidRuleId("x"), "MPR", "src", emptyMap(), null)
        }
    }
}
