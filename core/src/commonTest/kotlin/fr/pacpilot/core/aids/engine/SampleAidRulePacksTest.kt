package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidRule
import fr.pacpilot.core.shared.EffectiveDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the properties that keep the sample packs from ever being mistaken for a barème, and the
 * date arithmetic the M3-06 handover assertions rest on.
 */
class SampleAidRulePacksTest {

    @Test
    fun `every sample rule is provisional, and so is every pack`() {
        // The property that matters. One sourced-looking rule means a placeholder escaped and could
        // be read as authoritative (CLAUDE.md 12).
        SampleAidRulePacks.BOTH.forEach { pack ->
            pack.payload.aids.forEach { rule ->
                assertTrue(rule.isProvisional, "rule ${rule.id} in ${pack.version} looks sourced")
            }
            assertTrue(pack.payload.vatRate.isProvisional, "VAT rate in ${pack.version} looks sourced")
            assertTrue(pack.isProvisional, "pack ${pack.version} does not report itself provisional")
        }
    }

    @Test
    fun `each pack exercises all three aid mechanisms`() {
        // The evaluator is only proven by a fixture that reaches every branch of the sealed rule.
        SampleAidRulePacks.BOTH.forEach { pack ->
            val shapes = pack.payload.aids.map { rule ->
                when (rule) {
                    is AidRule.IncomeTiered -> "tiered"
                    is AidRule.Forfait -> "forfait"
                    is AidRule.RateBased -> "rate"
                }
            }
            assertEquals(setOf("tiered", "forfait", "rate"), shapes.toSet(), "in ${pack.version}")
        }
    }

    @Test
    fun `the two versions adjoin exactly, with no gap and no overlap`() {
        // effectiveTo is inclusive (M1-07): a bareme "applicable jusqu'au 30 juin" ends on the 30th
        // and its successor starts on the 1st. An exclusive end would leave the handover day
        // covered by nothing and a devis written that day irreproducible.
        val lastDayOfFirst = EffectiveDate(2025, 6, 30)
        val firstDayOfSecond = EffectiveDate(2025, 7, 1)

        assertTrue(SampleAidRulePacks.FIRST_HALF.covers(lastDayOfFirst))
        assertFalse(SampleAidRulePacks.SECOND_HALF.covers(lastDayOfFirst))

        assertTrue(SampleAidRulePacks.SECOND_HALF.covers(firstDayOfSecond))
        assertFalse(SampleAidRulePacks.FIRST_HALF.covers(firstDayOfSecond))
    }

    @Test
    fun `a date before the first pack is covered by neither`() {
        // The publication gap. It must stay a real, reachable condition — the engine's refusal path
        // depends on it.
        val beforeAnyPack = EffectiveDate(2024, 12, 31)
        assertTrue(SampleAidRulePacks.BOTH.none { it.covers(beforeAnyPack) })
    }

    @Test
    fun `the successor differs from its predecessor in every amount`() {
        // Not cosmetic. If the two packs agreed on a figure, a resolution that silently picked
        // "the latest pack" would still produce the expected number for a past date, and the M3-06
        // reproducibility assertions would pass while testing nothing.
        val first = SampleAidRulePacks.FIRST_HALF.payload
        val second = SampleAidRulePacks.SECOND_HALF.payload

        assertTrue(first.vatRate.rate != second.vatRate.rate)
        first.aids.forEach { rule ->
            val successor = second.rule(rule.id) ?: error("rule ${rule.id} vanished across the handover")
            assertTrue(rule != successor, "rule ${rule.id} is identical in both packs")
        }
    }
}
