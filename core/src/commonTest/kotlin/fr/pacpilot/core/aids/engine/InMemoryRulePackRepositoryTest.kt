package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.shared.EffectiveDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InMemoryRulePackRepositoryTest {

    private val repository = InMemoryRulePackRepository.withSamplePacks()

    private fun versionOn(year: Int, month: Int, day: Int): AidRulePackVersion? =
        repository.packEffectiveOn(EffectiveDate(year, month, day))?.version

    @Test
    fun `resolves the pack whose range contains the date`() {
        assertEquals(AidRulePackVersion("sample-2025-H1"), versionOn(2025, 3, 15))
        assertEquals(AidRulePackVersion("sample-2025-H2"), versionOn(2025, 9, 15))
    }

    @Test
    fun `both boundary days belong to their own pack`() {
        // The four days that decide whether the inclusive-end rule holds. effectiveTo is inclusive,
        // so the handover is 30 June / 1 July with no day owned by both and none owned by neither.
        assertEquals(AidRulePackVersion("sample-2025-H1"), versionOn(2025, 1, 1), "first day of H1")
        assertEquals(AidRulePackVersion("sample-2025-H1"), versionOn(2025, 6, 30), "last day of H1")
        assertEquals(AidRulePackVersion("sample-2025-H2"), versionOn(2025, 7, 1), "first day of H2")
        assertEquals(AidRulePackVersion("sample-2025-H2"), versionOn(2026, 1, 1), "H2 is open-ended")
    }

    @Test
    fun `a date covered by no pack resolves to null`() {
        // Distinguishable from every other outcome: not an empty pack, not the nearest one.
        assertNull(versionOn(2024, 12, 31), "the day before the first pack was published")
        assertNull(versionOn(2020, 6, 1))
    }

    @Test
    fun `overlapping packs fail loudly rather than resolving arbitrarily`() {
        // A publication error. Picking the newest silently would hide it until an auditor found it,
        // and every devis priced inside the overlap would already be suspect.
        val overlapping = InMemoryRulePackRepository(
            listOf(
                SampleAidRulePacks.FIRST_HALF,
                SampleAidRulePacks.SECOND_HALF.copy(effectiveFrom = EffectiveDate(2025, 6, 1)),
            ),
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            overlapping.packEffectiveOn(EffectiveDate(2025, 6, 15))
        }
        assertEquals(true, failure.message?.contains("overlap"))
    }

    @Test
    fun `resolution does not depend on the order the packs were added`() {
        // The likeliest silent regression is a first/last/sorted lookup. Reversing the fixture must
        // change nothing.
        val reversed = InMemoryRulePackRepository(SampleAidRulePacks.BOTH.reversed())
        listOf(
            EffectiveDate(2025, 1, 1),
            EffectiveDate(2025, 6, 30),
            EffectiveDate(2025, 7, 1),
            EffectiveDate(2026, 5, 5),
        ).forEach { date ->
            assertEquals(
                repository.packEffectiveOn(date)?.version,
                reversed.packEffectiveOn(date)?.version,
                "order changed the resolution on " + date.render(),
            )
        }
    }

    @Test
    fun `every day across the handover is covered exactly once`() {
        // Walks June and July day by day: no gap, no overlap, exactly one owner per day.
        val june = (1..30).map { EffectiveDate(2025, 6, it) }
        val july = (1..31).map { EffectiveDate(2025, 7, it) }

        june.forEach { assertEquals(AidRulePackVersion("sample-2025-H1"), repository.packEffectiveOn(it)?.version, it.render()) }
        july.forEach { assertEquals(AidRulePackVersion("sample-2025-H2"), repository.packEffectiveOn(it)?.version, it.render()) }
    }
}
