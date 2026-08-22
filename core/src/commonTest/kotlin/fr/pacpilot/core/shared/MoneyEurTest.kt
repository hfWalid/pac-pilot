package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs on both targets. Every assertion here is about a *string*, because the string is what the
 * golden-vector harness compares and what a homeowner reads off the screen — and it is the thing
 * `Double.toString()` would have rendered differently on the phone than on the server.
 */
class MoneyEurTest {

    @Test
    fun `renders two decimal places whatever the magnitude`() {
        assertEquals("1234.56", MoneyEur(123_456).render())
        assertEquals("0.07", MoneyEur(7).render())
        assertEquals("0.00", MoneyEur.ZERO.render())
    }

    @Test
    fun `renders a whole amount with its decimals, never as a bare integer`() {
        // The case that motivates the whole fixed-point design: a Double would render this as
        // "1.0" on the JVM and "1" in the browser, and the golden vectors would disagree.
        assertEquals("1.00", MoneyEur.ofEuros(1).render())
        assertEquals("12500.00", MoneyEur.ofEuros(12_500).render())
    }

    @Test
    fun `renders a negative amount with the sign in front of the whole part`() {
        assertEquals("-8.00", MoneyEur(-800).render())
        assertEquals("-0.05", MoneyEur(-5).render())
    }

    @Test
    fun `adds and subtracts exactly`() {
        assertEquals(MoneyEur(30), MoneyEur(10) + MoneyEur(20))
        assertEquals(MoneyEur(-10), MoneyEur(10) - MoneyEur(20))
    }

    @Test
    fun `scales by a whole quantity`() {
        assertEquals("375.00", (MoneyEur.ofEuros(125) * 3).render())
    }

    @Test
    fun `carries its unit in toString for diagnostics`() {
        assertEquals("1234.56 EUR", MoneyEur(123_456).toString())
    }

    @Test
    fun `adds tenths exactly, which any Double-backed implementation gets wrong`() {
        // The fastest regression check there is: 0.1 + 0.2 is 0.30000000000000004 in binary
        // floating point. In cents it is 30, and it is 30 on both targets.
        val tenth = MoneyEur(10)
        val fifth = MoneyEur(20)
        assertEquals(MoneyEur(30), tenth + fifth)
        assertEquals("0.30", (tenth + fifth).render())
    }
}
