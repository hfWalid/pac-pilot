package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PercentageTest {

    @Test
    fun `renders two decimal places of a percent`() {
        assertEquals("5.50", Percentage(550).render())
        assertEquals("30.00", Percentage.ofWholePercent(30).render())
        assertEquals("0.01", Percentage(1).render())
        assertEquals("0.00", Percentage.ZERO.render())
    }

    @Test
    fun `applies exactly when the result lands on a whole cent`() {
        // TVA 5,5 % of 12 500,00 EUR = 687,50 EUR, no rounding involved.
        assertEquals("687.50", Percentage(550).applyTo(MoneyEur.ofEuros(12_500)).render())
    }

    @Test
    fun `rounds a half away from zero`() {
        // 1,00 EUR * 5,5 % = 5,5 cents exactly — the boundary. Half goes away from zero, so 6.
        assertEquals(MoneyEur(6), Percentage(550).applyTo(MoneyEur(100)))
        // The same boundary on the other side of zero.
        assertEquals(MoneyEur(-6), Percentage(550).applyTo(MoneyEur(-100)))
    }

    @Test
    fun `rounds below the half towards zero`() {
        // 3,33 EUR * 5,5 % = 18,315 cents -> 18.
        assertEquals(MoneyEur(18), Percentage(550).applyTo(MoneyEur(333)))
        assertEquals(MoneyEur(-18), Percentage(550).applyTo(MoneyEur(-333)))
    }

    @Test
    fun `a zero rate yields nothing`() {
        assertEquals(MoneyEur.ZERO, Percentage.ZERO.applyTo(MoneyEur.ofEuros(9_999)))
    }

    @Test
    fun `refuses a negative rate`() {
        assertFailsWith<IllegalArgumentException> { Percentage(-1) }
    }
}
