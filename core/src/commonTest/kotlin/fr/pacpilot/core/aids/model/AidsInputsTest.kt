package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.MoneyEur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AidsInputsTest {

    private fun inputs(workCost: MoneyEur = MoneyEur.ofEuros(14_000)) = AidsInputs(
        incomeDecile = IncomeDecile(3),
        heatPumpType = HeatPumpType.AIR_WATER,
        climateZone = ClimateZone.H1,
        replacedSystem = ReplacedSystem.OIL_BOILER,
        workCost = workCost,
    )

    @Test
    fun `an income decile runs one to ten`() {
        assertEquals(1, IncomeDecile(1).value)
        assertEquals(10, IncomeDecile(10).value)
        assertFailsWith<IllegalArgumentException> { IncomeDecile(0) }
        assertFailsWith<IllegalArgumentException> { IncomeDecile(11) }
    }

    @Test
    fun `aids are computed against a positive work cost`() {
        assertFailsWith<IllegalArgumentException> { inputs(workCost = MoneyEur.ZERO) }
        assertFailsWith<IllegalArgumentException> { inputs(workCost = MoneyEur.ofEuros(-1)) }
    }

    @Test
    fun `a resolution derives the reste a charge from its aids`() {
        val resolution = AidsResolution(
            aids = ResolvedAids(
                AidRulePackVersion("2026-H1"),
                listOf(AidLine("MaPrimeRenov", MoneyEur.ofEuros(4_000), "anah.gouv.fr")),
            ),
            workCost = MoneyEur.ofEuros(14_000),
        )
        assertEquals("10000.00", resolution.resteACharge.amount.render())
        assertTrue(!resolution.resteACharge.isOverGranted)
    }
}
