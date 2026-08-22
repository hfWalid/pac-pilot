package fr.pacpilot.core.aids.engine

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The evaluator's behaviour against the H1 sample pack: income-tiered rising 1 000 EUR per decile
 * with no cap, a 500 EUR forfait, and 50 % of the work cost capped at 2 000 EUR.
 */
class AidsEngineTest {

    private val engine = AidsEngine(InMemoryRulePackRepository.withSamplePacks())

    /** Inside pack H1. Every assertion below prices against that version unless it says otherwise. */
    private val duringFirstHalf = EffectiveDate(2025, 3, 1)

    private fun inputs(
        decile: Int = 3,
        workCostEuros: Long = 10_000,
    ) = AidsInputs(
        incomeDecile = IncomeDecile(decile),
        heatPumpType = HeatPumpType.AIR_WATER,
        climateZone = ClimateZone.H1,
        replacedSystem = ReplacedSystem.OIL_BOILER,
        workCost = MoneyEur.ofEuros(workCostEuros),
    )

    private fun resolve(
        decile: Int = 3,
        workCostEuros: Long = 10_000,
        on: EffectiveDate = duringFirstHalf,
    ): AidsResolution {
        val outcome = engine.resolve(inputs(decile, workCostEuros), on)
        assertTrue(outcome is AidsOutcome.Resolved, "expected a resolution, got $outcome")
        return outcome.resolution
    }

    private fun AidsResolution.amountOf(ruleId: fr.pacpilot.core.aids.model.AidRuleId): MoneyEur? =
        aids.lines.firstOrNull { it.rule == ruleId }?.amount

    @Test
    fun `only the income-tiered aid moves when only the decile moves`() {
        val low = resolve(decile = 2)
        val high = resolve(decile = 7)

        assertEquals("2000.00", low.amountOf(SampleAidRulePacks.INCOME_TIERED)?.render())
        assertEquals("7000.00", high.amountOf(SampleAidRulePacks.INCOME_TIERED)?.render())

        assertEquals(low.amountOf(SampleAidRulePacks.FORFAIT), high.amountOf(SampleAidRulePacks.FORFAIT))
        assertEquals(low.amountOf(SampleAidRulePacks.RATE_BASED), high.amountOf(SampleAidRulePacks.RATE_BASED))
    }

    @Test
    fun `only the rate-based aid moves when only the work cost moves`() {
        // Both work costs stay under the cap so the rate is what is being observed, not the ceiling.
        val small = resolve(workCostEuros = 1_000)
        val large = resolve(workCostEuros = 3_000)

        assertEquals("500.00", small.amountOf(SampleAidRulePacks.RATE_BASED)?.render())
        assertEquals("1500.00", large.amountOf(SampleAidRulePacks.RATE_BASED)?.render())

        assertEquals(small.amountOf(SampleAidRulePacks.FORFAIT), large.amountOf(SampleAidRulePacks.FORFAIT))
        assertEquals(small.amountOf(SampleAidRulePacks.INCOME_TIERED), large.amountOf(SampleAidRulePacks.INCOME_TIERED))
    }

    @Test
    fun `the forfait is independent of both`() {
        assertEquals("500.00", resolve(decile = 1, workCostEuros = 1_000).amountOf(SampleAidRulePacks.FORFAIT)?.render())
        assertEquals("500.00", resolve(decile = 9, workCostEuros = 90_000).amountOf(SampleAidRulePacks.FORFAIT)?.render())
    }

    @Test
    fun `a rate-based aid clamps at its cap and not a cent before`() {
        // 50 % of the work cost against a 2 000 EUR ceiling. The three cases that matter are the
        // cent below the cap, the cap exactly, and the cent above it.
        val justUnder = engine.resolve(
            inputs().copy(workCost = MoneyEur(399_998)), // 3 999,98 EUR -> 1 999,99
            duringFirstHalf,
        )
        val exactly = engine.resolve(
            inputs().copy(workCost = MoneyEur(400_000)), // 4 000,00 EUR -> 2 000,00
            duringFirstHalf,
        )
        val justOver = engine.resolve(
            inputs().copy(workCost = MoneyEur(400_002)), // 4 000,02 EUR -> 2 000,01, clamped
            duringFirstHalf,
        )

        fun rateAid(outcome: AidsOutcome): String {
            assertTrue(outcome is AidsOutcome.Resolved)
            return outcome.resolution.amountOf(SampleAidRulePacks.RATE_BASED)!!.render()
        }

        assertEquals("1999.99", rateAid(justUnder), "clamped a cent early")
        assertEquals("2000.00", rateAid(exactly))
        assertEquals("2000.00", rateAid(justOver), "did not clamp")
    }

    @Test
    fun `a tier the pack does not publish produces no line at all`() {
        // Decile 10 is absent from the sample tier table. The distinction is the point: no line
        // says "this scheme was not in play", a zero line would say "you get nothing from it".
        val excluded = resolve(decile = 10)

        assertNull(excluded.amountOf(SampleAidRulePacks.INCOME_TIERED), "an excluded tier produced a line")
        assertEquals(2, excluded.aids.lines.size, "the other two rules should still apply")
        assertTrue(excluded.aids.lines.none { it.amount == MoneyEur.ZERO }, "a zero line appeared")
    }

    @Test
    fun `a date no published pack covers is refused rather than priced`() {
        // Not an exception, not empty aids, and above all not the nearest pack: an empty resolution
        // would show a reste-a-charge equal to the full price, which reads as a real answer.
        val before = EffectiveDate(2024, 12, 31)
        val outcome = engine.resolve(inputs(), before)

        assertTrue(outcome is AidsOutcome.NoPackPublished, "expected a refusal, got $outcome")
        assertEquals(before, outcome.effectiveDate)
    }

    @Test
    fun `every line carries the rule that produced it and that rule's own source`() {
        val resolution = resolve()

        assertTrue(resolution.aids.lines.isNotEmpty())
        resolution.aids.lines.forEach { line ->
            val rule = SampleAidRulePacks.FIRST_HALF.payload.rule(line.rule)
                ?: error("line ${line.rule} names no rule in the pack it came from")
            assertEquals(rule.label, line.label)
            assertEquals(rule.source, line.source)
            assertTrue(line.source.startsWith("SOURCE_TBD"), "a sample line looks sourced")
        }
    }
}
