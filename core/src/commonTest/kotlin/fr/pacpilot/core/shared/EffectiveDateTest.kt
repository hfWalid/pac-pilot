package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EffectiveDateTest {

    @Test
    fun `renders ISO-8601 with zero padding so dates sort as text`() {
        assertEquals("2026-08-21", EffectiveDate(2026, 8, 21).render())
        assertEquals("2026-01-01", EffectiveDate(2026, 1, 1).render())
        assertEquals("0999-12-31", EffectiveDate(999, 12, 31).render())
    }

    @Test
    fun `orders by year then month then day`() {
        assertTrue(EffectiveDate(2025, 12, 31) < EffectiveDate(2026, 1, 1))
        assertTrue(EffectiveDate(2026, 1, 31) < EffectiveDate(2026, 2, 1))
        assertTrue(EffectiveDate(2026, 6, 29) < EffectiveDate(2026, 6, 30))
        assertEquals(0, EffectiveDate(2026, 6, 30).compareTo(EffectiveDate(2026, 6, 30)))
    }

    @Test
    fun `accepts the twenty-ninth of February in a leap year`() {
        assertEquals("2024-02-29", EffectiveDate(2024, 2, 29).render())
    }

    @Test
    fun `refuses the twenty-ninth of February in a common year`() {
        assertFailsWith<IllegalArgumentException> { EffectiveDate(2026, 2, 29) }
    }

    @Test
    fun `applies the full Gregorian century rule`() {
        // 1900 is not a leap year, 2000 is. A naive "divisible by four" gets 1900 wrong, and a
        // barème date range crossing a century boundary would silently accept an impossible day.
        assertTrue(EffectiveDate.isLeapYear(2024))
        assertTrue(EffectiveDate.isLeapYear(2000))
        assertTrue(!EffectiveDate.isLeapYear(1900))
        assertTrue(!EffectiveDate.isLeapYear(2026))
        assertEquals(28, EffectiveDate.lengthOfMonth(1900, 2))
        assertEquals(29, EffectiveDate.lengthOfMonth(2000, 2))
    }

    @Test
    fun `refuses a day past the end of its month`() {
        assertFailsWith<IllegalArgumentException> { EffectiveDate(2026, 4, 31) }
        assertFailsWith<IllegalArgumentException> { EffectiveDate(2026, 1, 32) }
        assertFailsWith<IllegalArgumentException> { EffectiveDate(2026, 1, 0) }
    }

    @Test
    fun `refuses a month outside the year`() {
        assertFailsWith<IllegalArgumentException> { EffectiveDate(2026, 0, 1) }
        assertFailsWith<IllegalArgumentException> { EffectiveDate(2026, 13, 1) }
    }
}
