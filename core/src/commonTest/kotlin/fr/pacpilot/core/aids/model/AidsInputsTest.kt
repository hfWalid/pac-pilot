package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage
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
    fun `a resolution derives the reste a charge from the TTC total and its aids`() {
        // 14 000 HT + 5,5 % TVA = 14 770,00 TTC, less a 4 000 aid = 10 770,00.
        // M3-04 moved this base from HT to TTC so it agrees with Quote.resteACharge: aids are paid
        // toward what the client is invoiced, and the VAT is part of that.
        val resolution = AidsResolution(
            aids = ResolvedAids(
                AidRulePackVersion("2026-H1"),
                listOf(AidLine(AidRuleId("mpr"), "MaPrimeRenov", MoneyEur.ofEuros(4_000), "anah.gouv.fr")),
            ),
            workCost = MoneyEur.ofEuros(14_000),
            appliedVatRate = VatRate(Percentage(550), "SOURCE_TBD (fixture)"),
        )
        assertEquals("770.00", resolution.vat.render())
        assertEquals("14770.00", resolution.totalIncludingVat.render())
        assertEquals("10770.00", resolution.resteACharge.amount.render())
        assertTrue(!resolution.resteACharge.isOverGranted)
    }
}

class IncomeDeciletPrivacyTest {

    @Test
    fun `the fiscal income decile never renders itself into a log line`() {
        // 4.6 treats fiscal income as sensitive. data class would have generated a toString that
        // prints the value, and every enclosing data class renders its fields through it.
        val decile = IncomeDecile(7)
        assertEquals("IncomeDecile(redacted)", decile.toString())
        assertEquals(7, decile.value, "the value is still readable when deliberately asked for")

        val inputs = AidsInputs(
            incomeDecile = decile,
            heatPumpType = HeatPumpType.AIR_WATER,
            climateZone = fr.pacpilot.core.shared.ClimateZone.H1,
            replacedSystem = ReplacedSystem.OIL_BOILER,
            workCost = fr.pacpilot.core.shared.MoneyEur.ofEuros(14_000),
        )
        assertTrue(inputs.toString().contains("redacted"))
        assertTrue(!inputs.toString().contains("IncomeDecile(7)"))
    }
}
