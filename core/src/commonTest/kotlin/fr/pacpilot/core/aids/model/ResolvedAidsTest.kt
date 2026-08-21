package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.MoneyEur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResolvedAidsTest {

    private val packVersion = AidRulePackVersion("2026-H1")

    private fun aids(vararg amounts: Long) = ResolvedAids(
        packVersion,
        amounts.mapIndexed { index, cents ->
            AidLine("Aide $index", MoneyEur(cents), "fiche BAR-TH-171")
        },
    )

    @Test
    fun `totals its lines exactly`() {
        assertEquals("5400.00", aids(400_000, 140_000).total.render())
    }

    @Test
    fun `totals to nothing when no aid applies`() {
        assertEquals(MoneyEur.ZERO, ResolvedAids.none(packVersion).total)
    }

    @Test
    fun `reste a charge is the work cost less the aids`() {
        val resteACharge = ResteACharge.of(MoneyEur.ofEuros(14_000), aids(400_000, 140_000))
        assertEquals("8600.00", resteACharge.amount.render())
        assertTrue(!resteACharge.isOverGranted)
    }

    @Test
    fun `reste a charge goes negative and flags rather than clamping at zero`() {
        // Aids exceeding the work cost means a barème was misapplied. Flooring the number at 0,00
        // would hide that behind a figure the homeowner would find entirely plausible.
        val resteACharge = ResteACharge.of(MoneyEur.ofEuros(3_000), aids(400_000))
        assertEquals("-1000.00", resteACharge.amount.render())
        assertTrue(resteACharge.isOverGranted)
    }

    @Test
    fun `an aid line must cite the fiche it comes from`() {
        assertFailsWith<IllegalArgumentException> {
            AidLine("MaPrimeRenov", MoneyEur.ofEuros(4_000), "")
        }
    }

    @Test
    fun `an aid line does not take money away`() {
        assertFailsWith<IllegalArgumentException> {
            AidLine("MaPrimeRenov", MoneyEur(-1), "fiche BAR-TH-171")
        }
    }
}
