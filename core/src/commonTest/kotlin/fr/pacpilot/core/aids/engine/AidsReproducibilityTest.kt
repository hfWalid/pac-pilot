package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.AidsResolution
import fr.pacpilot.core.aids.model.HeatPumpType
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.ReplacedSystem
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The property the product is sold on, and the reason the whole pack architecture exists.
 *
 * MaPrimeRénov' plafonds, CEE forfaits and TVA conditions move two to four times a year. A devis
 * issued in 2025 and questioned in 2028 has to recompute to the figures it showed, against the
 * barème in force on **its own** date, long after that barème has been superseded — otherwise the
 * conversation stops being about what the rules said then and becomes about why the tool disagrees
 * with itself.
 *
 * Both sample packs are published throughout every test here. The newer one is always available and
 * must never win on a date it does not cover. Time never passes: every date is a literal input,
 * which is exactly the point.
 */
class AidsReproducibilityTest {

    private val engine = AidsEngine(InMemoryRulePackRepository.withSamplePacks())

    private val H1 = AidRulePackVersion("sample-2025-H1")
    private val H2 = AidRulePackVersion("sample-2025-H2")

    private val inputs = AidsInputs(
        incomeDecile = IncomeDecile(5),
        heatPumpType = HeatPumpType.AIR_WATER,
        climateZone = ClimateZone.H1,
        replacedSystem = ReplacedSystem.OIL_BOILER,
        workCost = MoneyEur.ofEuros(20_000),
    )

    private fun resolvedOn(date: EffectiveDate): AidsResolution {
        val outcome = engine.resolve(inputs, date)
        assertTrue(outcome is AidsOutcome.Resolved, "expected a resolution on ${date.render()}, got $outcome")
        return outcome.resolution
    }

    @Test
    fun `a past devis resolves the older pack while a newer one is published`() {
        // The failure mode this test exists for: "latest wins". Both packs are in the repository;
        // only the date decides.
        val past = resolvedOn(EffectiveDate(2025, 3, 1))
        val current = resolvedOn(EffectiveDate(2026, 3, 1))

        assertEquals(H1, past.aids.packVersion)
        assertEquals(H2, current.aids.packVersion)

        // And the two genuinely disagree, so the assertion above could not pass by coincidence.
        assertNotEquals(past.aids.total, current.aids.total)
        assertNotEquals(past.appliedVatRate.rate, current.appliedVatRate.rate)
    }

    @Test
    fun `the figures follow the pack across the handover, not the calendar`() {
        // Decile 5: H1 pays 5 x 1 000, H2 pays 5 x 2 000. Forfait 500 -> 800.
        // Rate 50 % capped 2 000 -> 25 % capped 3 000, on 20 000 HT: both caps bite, so the
        // rate-based line is the ceiling in each pack rather than the rate.
        val lastDayOfH1 = resolvedOn(EffectiveDate(2025, 6, 30))
        val firstDayOfH2 = resolvedOn(EffectiveDate(2025, 7, 1))

        assertEquals(H1, lastDayOfH1.aids.packVersion)
        assertEquals("7500.00", lastDayOfH1.aids.total.render()) // 5 000 + 500 + 2 000
        assertEquals("22000.00", lastDayOfH1.totalIncludingVat.render()) // 10 % TVA

        assertEquals(H2, firstDayOfH2.aids.packVersion)
        assertEquals("13800.00", firstDayOfH2.aids.total.render()) // 10 000 + 800 + 3 000 (capped)
        assertEquals("24000.00", firstDayOfH2.totalIncludingVat.render()) // 20 % TVA
    }

    @Test
    fun `every boundary day belongs to exactly the pack that published it`() {
        listOf(
            EffectiveDate(2025, 1, 1) to H1,
            EffectiveDate(2025, 6, 29) to H1,
            EffectiveDate(2025, 6, 30) to H1,
            EffectiveDate(2025, 7, 1) to H2,
            EffectiveDate(2025, 7, 2) to H2,
        ).forEach { (date, expected) ->
            assertEquals(expected, resolvedOn(date).aids.packVersion, "on ${date.render()}")
        }
    }

    @Test
    fun `recomputing a stored resolution from its inputs and its own date reproduces it exactly`() {
        // What M4 wires as the server-side verifier and M8 compares across client and server.
        // Exercised here so both are an integration problem rather than a discovery problem.
        val devisDate = EffectiveDate(2025, 4, 15)
        val asIssued = resolvedOn(devisDate)

        val recomputed = resolvedOn(devisDate)

        assertEquals(asIssued.aids.packVersion, recomputed.aids.packVersion)
        assertEquals(asIssued.aids.lines, recomputed.aids.lines, "the itemised lines must match, not just the total")
        assertEquals(asIssued.aids.total, recomputed.aids.total)
        assertEquals(asIssued.vat, recomputed.vat)
        assertEquals(asIssued.totalIncludingVat, recomputed.totalIncludingVat)
        assertEquals(asIssued.resteACharge, recomputed.resteACharge)
    }

    @Test
    fun `a resolution issued under the old pack is unaffected by the newer one existing`() {
        // The same assertion from the auditor's side: a repository holding only the old pack and one
        // holding both must price an old devis identically. If they differ, something reached for
        // "the current pack" instead of the devis date.
        val devisDate = EffectiveDate(2025, 2, 10)

        val whenOnlyH1WasPublished = AidsEngine(
            InMemoryRulePackRepository(listOf(SampleAidRulePacks.FIRST_HALF)),
        ).resolve(inputs, devisDate)
        val todayWithBothPublished = engine.resolve(inputs, devisDate)

        assertTrue(whenOnlyH1WasPublished is AidsOutcome.Resolved)
        assertTrue(todayWithBothPublished is AidsOutcome.Resolved)
        assertEquals(
            whenOnlyH1WasPublished.resolution.aids.lines,
            todayWithBothPublished.resolution.aids.lines,
        )
        assertEquals(
            whenOnlyH1WasPublished.resolution.resteACharge,
            todayWithBothPublished.resolution.resteACharge,
        )
    }

    @Test
    fun `a devis dated before any published pack is refused, not priced against the nearest one`() {
        val outcome = engine.resolve(inputs, EffectiveDate(2024, 11, 30))

        assertTrue(outcome is AidsOutcome.NoPackPublished)
        assertEquals(EffectiveDate(2024, 11, 30), outcome.effectiveDate)
    }

    @Test
    fun `resolution is unaffected by the order the packs were published into the repository`() {
        val reversed = AidsEngine(InMemoryRulePackRepository(SampleAidRulePacks.BOTH.reversed()))

        listOf(
            EffectiveDate(2025, 3, 1),
            EffectiveDate(2025, 6, 30),
            EffectiveDate(2025, 7, 1),
            EffectiveDate(2026, 3, 1),
        ).forEach { date ->
            val fromReversed = reversed.resolve(inputs, date)
            assertTrue(fromReversed is AidsOutcome.Resolved)
            assertEquals(resolvedOn(date).aids, fromReversed.resolution.aids, "on ${date.render()}")
        }
    }
}
