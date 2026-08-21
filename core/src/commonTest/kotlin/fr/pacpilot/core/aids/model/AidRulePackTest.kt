package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.EffectiveDate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AidRulePackTest {

    private val closedPack = AidRulePack(
        version = AidRulePackVersion("2026-H1"),
        effectiveFrom = EffectiveDate(2026, 1, 1),
        effectiveTo = EffectiveDate(2026, 6, 30),
        checksum = "sha256:abc",
    )

    @Test
    fun `covers both boundary days inclusively`() {
        // "applicable jusqu'au 30 juin" ends on the 30th. An exclusive end would leave that day
        // priced by no pack at all, and a devis written on it irreproducible.
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
        val successor = AidRulePack(
            version = AidRulePackVersion("2026-H2"),
            effectiveFrom = EffectiveDate(2026, 7, 1),
            effectiveTo = null,
            checksum = "sha256:def",
        )
        val handoverDay = EffectiveDate(2026, 7, 1)
        assertTrue(!closedPack.covers(handoverDay))
        assertTrue(successor.covers(handoverDay))
        assertTrue(closedPack.covers(EffectiveDate(2026, 6, 30)))
        assertTrue(!successor.covers(EffectiveDate(2026, 6, 30)))
    }

    @Test
    fun `an open-ended pack covers every day from its start onward`() {
        val open = closedPack.copy(effectiveTo = null)
        assertTrue(open.covers(EffectiveDate(2099, 12, 31)))
        assertTrue(!open.covers(EffectiveDate(2025, 12, 31)))
    }

    @Test
    fun `refuses a range that runs backward`() {
        assertFailsWith<IllegalArgumentException> {
            closedPack.copy(effectiveFrom = EffectiveDate(2026, 7, 1), effectiveTo = EffectiveDate(2026, 1, 1))
        }
    }

    @Test
    fun `refuses a pack published without a checksum`() {
        assertFailsWith<IllegalArgumentException> { closedPack.copy(checksum = "  ") }
    }
}
