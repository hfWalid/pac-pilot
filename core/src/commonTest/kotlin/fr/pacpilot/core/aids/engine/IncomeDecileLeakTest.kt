package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.HeatPumpType
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.ReplacedSystem
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Adversarial sweep of every surface the fiscal income decile can reach once it stops being a field
 * on a model and starts flowing through an engine (§4.6).
 *
 * The leak vector is Kotlin's generated `toString`: it renders every field, and every enclosing
 * data class renders its own fields through it. One `"inputs=$inputs"` in a log statement, or one
 * exception message built by string interpolation, is enough to put a household's income band into
 * a file that outlives the request and was never scoped to hold it.
 *
 * Rendering is checked structurally — for `IncomeDecile(<digit>)` and `incomeDecile=<digit>` —
 * rather than by hunting a bare digit. A bare digit search cannot work here: the aid this decile
 * selects is itself an amount, so the digit legitimately appears in figures the devis must show.
 */
class IncomeDecileLeakTest {

    private val engine = AidsEngine(InMemoryRulePackRepository.withSamplePacks())
    private val duringFirstHalf = EffectiveDate(2025, 3, 1)

    private fun inputsWith(decile: Int) = AidsInputs(
        incomeDecile = IncomeDecile(decile),
        heatPumpType = HeatPumpType.AIR_WATER,
        climateZone = ClimateZone.H1,
        replacedSystem = ReplacedSystem.OIL_BOILER,
        workCost = MoneyEur.ofEuros(20_000),
    )

    /** Every way a decile could be spelt into a rendered string, for a given value. */
    private fun leakPatternsFor(decile: Int): List<String> = listOf(
        "IncomeDecile($decile)",
        "incomeDecile=$decile",
        "decile=$decile",
        "decile $decile",
    )

    private fun assertNoLeak(rendered: String, decile: Int, surface: String) {
        leakPatternsFor(decile).forEach { pattern ->
            assertFalse(rendered.contains(pattern), "$surface leaked the decile as '$pattern': $rendered")
        }
    }

    @Test
    fun `no object on the aids path renders the decile, at any tier`() {
        // Every published tier, not one sample: a redaction that held only for the value someone
        // happened to test with would be worse than none.
        (1..10).forEach { decile ->
            val inputs = inputsWith(decile)
            assertNoLeak(inputs.toString(), decile, "AidsInputs")
            assertNoLeak(IncomeDecile(decile).toString(), decile, "IncomeDecile")

            val outcome = engine.resolve(inputs, duringFirstHalf)
            assertTrue(outcome is AidsOutcome.Resolved)

            assertNoLeak(outcome.toString(), decile, "AidsOutcome")
            assertNoLeak(outcome.resolution.toString(), decile, "AidsResolution")
            assertNoLeak(outcome.resolution.aids.toString(), decile, "ResolvedAids")
            assertNoLeak(outcome.resolution.resteACharge.toString(), decile, "ResteACharge")
            outcome.resolution.aids.lines.forEach { line ->
                assertNoLeak(line.toString(), decile, "AidLine")
            }
        }
    }

    @Test
    fun `an income-tiered aid line names the scheme and never the tier that matched`() {
        // The label reaches a document the client keeps. "MaPrimeRenov'" is fine; "MaPrimeRenov',
        // decile 3" is a household's fiscal position printed on a devis, filed, and forwarded.
        (1..9).forEach { decile ->
            val outcome = engine.resolve(inputsWith(decile), duringFirstHalf)
            assertTrue(outcome is AidsOutcome.Resolved)

            val tiered = outcome.resolution.aids.lines.first { it.rule == SampleAidRulePacks.INCOME_TIERED }
            assertFalse(
                tiered.label.any { character -> character.isDigit() },
                "an aid line label carries a digit, which is how a tier gets onto a devis: ${tiered.label}",
            )
            assertNoLeak(tiered.label, decile, "AidLine.label")
        }
    }

    @Test
    fun `the decile is still readable when deliberately asked for`() {
        // Redaction must not become obstruction: the engine has to select a tier with it, and M4's
        // persistence has to store it. What is refused is *rendering*, not access.
        assertTrue((1..10).all { decile -> IncomeDecile(decile).value == decile })
    }

    @Test
    fun `rejecting an out-of-range decile does not quote it back`() {
        // Asserted as a constant rather than by searching the message for the rejected digit. The
        // message legitimately contains "1..10", so a digit search reports a leak for 0, 1 and 10 —
        // the same false positive the class docstring warns about, and worth pinning here because
        // the naive version of this test passes for 11 and fails for 0.
        //
        // A message that is a fixed string cannot interpolate anything, which is the property the
        // rule needs: not "this value did not appear" but "no value can".
        val expected = "an income decile is 1..10"

        listOf(0, 11, -3, 99).forEach { rejected ->
            val failure = assertFailsWith<IllegalArgumentException> { IncomeDecile(rejected) }
            assertTrue(
                failure.message == expected,
                "the validation message is no longer a constant, so it can interpolate: ${failure.message}",
            )
        }
    }
}
