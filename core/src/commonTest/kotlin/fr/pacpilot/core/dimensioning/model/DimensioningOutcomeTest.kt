package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.PowerKw
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DimensioningOutcomeTest {

    private fun result() = HeatLoadResult(
        heatLoad = PowerKw(12_400),
        recommendedPowerBand = PowerBand(PowerKw(11_000), PowerKw(14_000)),
        recommendedFlowTemperature = null,
        assumptions = AssumptionsLog(listOf(Assumption("U-value from period", Assumption.SOURCE_TBD))),
    )

    @Test
    fun `a computed outcome carries the result`() {
        val outcome: DimensioningOutcome = DimensioningOutcome.Computed(result())
        assertTrue(outcome is DimensioningOutcome.Computed)
        assertEquals(PowerKw(12_400), outcome.result.heatLoad)
    }

    @Test
    fun `a refusal carries reasons and no heat load to read`() {
        // The compile-time half of this guarantee cannot be asserted at runtime: ManualStudyRequired
        // simply has no result member, so `outcome.result` does not compile against it. What is
        // checkable here is that exhaustive handling is forced and the refusal branch yields no
        // number a caller could mistake for an answer.
        val outcome: DimensioningOutcome = DimensioningOutcome.ManualStudyRequired(
            listOf(RefusalReason("Surface habitable au-dela de l'enveloppe validee")),
        )

        val rendered = when (outcome) {
            is DimensioningOutcome.Computed -> outcome.result.heatLoad.render()
            is DimensioningOutcome.ManualStudyRequired -> "etude manuelle requise"
        }
        assertEquals("etude manuelle requise", rendered)
    }

    @Test
    fun `a refusal states at least one reason`() {
        assertFailsWith<IllegalArgumentException> {
            DimensioningOutcome.ManualStudyRequired(emptyList())
        }
    }

    @Test
    fun `a refusal reason is not blank`() {
        assertFailsWith<IllegalArgumentException> { RefusalReason("  ") }
    }
}
