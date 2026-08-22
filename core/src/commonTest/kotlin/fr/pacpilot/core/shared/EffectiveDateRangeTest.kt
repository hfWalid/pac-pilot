package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EffectiveDateRangeTest {

    private val firstHalf = EffectiveDateRange(EffectiveDate(2026, 1, 1), EffectiveDate(2026, 6, 30))

    @Test
    fun `both boundary days belong to the range`() {
        assertTrue(firstHalf.contains(EffectiveDate(2026, 1, 1)))
        assertTrue(firstHalf.contains(EffectiveDate(2026, 6, 30)))
    }

    @Test
    fun `the days either side do not`() {
        assertTrue(!firstHalf.contains(EffectiveDate(2025, 12, 31)))
        assertTrue(!firstHalf.contains(EffectiveDate(2026, 7, 1)))
    }

    @Test
    fun `a successor starting the following day leaves no gap and no overlap`() {
        val secondHalf = EffectiveDateRange(EffectiveDate(2026, 7, 1), null)
        val handover = EffectiveDate(2026, 7, 1)
        assertTrue(!firstHalf.contains(handover))
        assertTrue(secondHalf.contains(handover))
        assertTrue(firstHalf.contains(EffectiveDate(2026, 6, 30)))
        assertTrue(!secondHalf.contains(EffectiveDate(2026, 6, 30)))
    }

    @Test
    fun `an open-ended range covers every day from its start onward`() {
        val open = EffectiveDateRange(EffectiveDate(2026, 1, 1), null)
        assertTrue(open.isOpenEnded)
        assertTrue(open.contains(EffectiveDate(2099, 12, 31)))
        assertTrue(!open.contains(EffectiveDate(2025, 12, 31)))
    }

    @Test
    fun `a single day is a valid range`() {
        val oneDay = EffectiveDateRange(EffectiveDate(2026, 3, 1), EffectiveDate(2026, 3, 1))
        assertTrue(oneDay.contains(EffectiveDate(2026, 3, 1)))
    }

    @Test
    fun `refuses a range that runs backward`() {
        assertFailsWith<IllegalArgumentException> {
            EffectiveDateRange(EffectiveDate(2026, 7, 1), EffectiveDate(2026, 1, 1))
        }
    }
}
