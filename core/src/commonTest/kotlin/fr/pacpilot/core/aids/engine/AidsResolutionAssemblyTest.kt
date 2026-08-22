package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.AidsResolution
import fr.pacpilot.core.aids.model.HeatPumpType
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.ReplacedSystem
import fr.pacpilot.core.aids.model.ResteACharge
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The output side: provenance survives all the way out, totals stay derived, and a figure that
 * disagrees with itself flags rather than being normalised.
 */
class AidsResolutionAssemblyTest {

    private val engine = AidsEngine(InMemoryRulePackRepository.withSamplePacks())
    private val duringFirstHalf = EffectiveDate(2025, 3, 1)

    private fun resolve(decile: Int = 4, workCost: MoneyEur = MoneyEur.ofEuros(20_000)): AidsResolution {
        val outcome = engine.resolve(
            AidsInputs(
                incomeDecile = IncomeDecile(decile),
                heatPumpType = HeatPumpType.AIR_WATER,
                climateZone = ClimateZone.H1,
                replacedSystem = ReplacedSystem.OIL_BOILER,
                workCost = workCost,
            ),
            duringFirstHalf,
        )
        assertTrue(outcome is AidsOutcome.Resolved)
        return outcome.resolution
    }

    @Test
    fun `a reste a charge names its bareme without help from the rest of the resolution`() {
        // The property M1-review closed. Hold only the ResteACharge — pass it anywhere on its own —
        // and the version that produced it is still reachable. If this regresses, a figure the
        // homeowner remembers becomes irreproducible the moment it travels alone.
        val resteACharge: ResteACharge = resolve().estimatedResteACharge

        assertEquals(AidRulePackVersion("sample-2025-H1"), resteACharge.packVersion)
    }

    @Test
    fun `the pack version is reachable from the aids and from the reste a charge independently`() {
        val resolution = resolve()

        assertEquals(AidRulePackVersion("sample-2025-H1"), resolution.aids.packVersion)
        assertEquals(resolution.aids.packVersion, resolution.estimatedResteACharge.packVersion)
    }

    @Test
    fun `the total is the sum of the lines, across mechanisms of different shapes`() {
        // Decile 4 -> 4 000 tiered, 500 forfait, 50 % of 20 000 capped at 2 000 -> 2 000.
        val resolution = resolve()

        assertEquals(3, resolution.aids.lines.size)
        val summed = resolution.aids.lines.fold(MoneyEur.ZERO) { running, line -> running + line.amount }
        assertEquals(summed, resolution.aids.total)
        assertEquals("6500.00", resolution.aids.total.render())
    }

    @Test
    fun `every figure is derived, so a correction to one line moves the total and the reste a charge`() {
        // The reason nothing here is stored: a stored total is a second source of truth that drifts
        // from its lines the first time one is corrected, and it is the number read off the screen.
        val resolution = resolve()
        val corrected = resolution.copy(
            aids = resolution.aids.copy(lines = resolution.aids.lines.drop(1)),
        )

        assertTrue(corrected.aids.total < resolution.aids.total)
        assertTrue(corrected.estimatedResteACharge.amount > resolution.estimatedResteACharge.amount)
    }

    @Test
    fun `aids exceeding the invoice flag rather than clamping at zero`() {
        // A small job against the sample pack's generous top tiers. 100 HT -> 110 TTC, against
        // 9 000 + 500 + 50 in aids. Flooring this at 0,00 would hide a misapplied bareme behind a
        // number that looks fine; the standing rule is persist and flag (CLAUDE.md 4.2).
        val resolution = resolve(decile = 9, workCost = MoneyEur.ofEuros(100))

        assertEquals("110.00", resolution.estimatedTotalIncludingVat.render())
        assertEquals("9550.00", resolution.aids.total.render())
        assertEquals("-9440.00", resolution.estimatedResteACharge.amount.render())
        assertTrue(resolution.estimatedResteACharge.isOverGranted)
    }
}
