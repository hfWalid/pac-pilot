package fr.pacpilot.core.quoting.engine

import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.dimensioning.model.Assumption
import fr.pacpilot.core.dimensioning.model.AssumptionsLog
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.Dimensioning
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.HeatLoadResult
import fr.pacpilot.core.dimensioning.model.InputsSnapshot
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.quoting.model.LineItem
import fr.pacpilot.core.quoting.model.ProductSnapshot
import fr.pacpilot.core.quoting.model.QuoteStatus
import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.DimensioningId
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.ElectricalSupplyKva
import fr.pacpilot.core.shared.InstallerId
import fr.pacpilot.core.shared.InstantUtc
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.PowerBand
import fr.pacpilot.core.shared.PowerKw
import fr.pacpilot.core.shared.ProductId
import fr.pacpilot.core.shared.QuoteId
import fr.pacpilot.core.shared.SiteId
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuoteAssemblerTest {

    private val assembler = QuoteAssembler { QuoteId("quote-1") }

    private fun study(on: EffectiveDate): ValidatedDimensioning = Dimensioning.computed(
        id = DimensioningId("dim-1"),
        siteId = SiteId("site-1"),
        inputs = InputsSnapshot(
            surface = SurfaceM2(12_000),
            ceilingHeight = CeilingHeightM(250),
            constructionPeriod = ConstructionPeriod.BEFORE_1975,
            insulationLevel = InsulationLevel.PARTIAL,
            ventilationType = VentilationType.VMC_SIMPLE_FLUX,
            emitterType = EmitterType.RADIATOR_HIGH_TEMPERATURE,
            climateZone = ClimateZone.H1,
            baseTemperature = TemperatureC(-70),
            targetIndoorTemperature = TemperatureC(190),
            availableElectricalPower = ElectricalSupplyKva(9),
        ),
        outcome = DimensioningOutcome.Computed(
            HeatLoadResult(
                heatLoad = PowerKw(19_032),
                recommendedPowerBand = PowerBand(PowerKw(17_129), PowerKw(22_838)),
                recommendedFlowTemperature = TemperatureC(500),
                assumptions = AssumptionsLog(listOf(Assumption("U-value", "SOURCE_TBD"))),
            ),
        ),
        effectiveDate = on,
    ).validate(InstallerId("installer-1"), InstantUtc(1_000))

    private val product = ProductSnapshot(
        id = ProductId("product-1"),
        model = "ECHANTILLON 12 kW",
        powerAtMinusSevenC = PowerKw(12_000),
        priceAtQuoteTime = MoneyEur.ofEuros(9_500),
    )

    private val lines = listOf(LineItem("PAC air-eau", MoneyEur.ofEuros(9_500), 1, Percentage(550)))

    private fun aids() = ResolvedAids.none(AidRulePackVersion("sample-2025-H1"))

    @Test
    fun `a devis is born a draft, on the id the caller supplied`() {
        val quote = assembler.build(
            study(EffectiveDate(2025, 3, 1)),
            product,
            lines,
            aids(),
            EffectiveDate(2025, 3, 1),
        )

        assertEquals(QuoteId("quote-1"), quote.id)
        assertEquals(QuoteStatus.DRAFT, quote.status)
        assertEquals("9500.00", quote.subtotalExcludingVat.render())
    }

    @Test
    fun `a devis may be dated after the study it quotes`() {
        // The normal case: the pre-visit happens, the devis follows a few days later.
        val quote = assembler.build(
            study(EffectiveDate(2025, 3, 1)),
            product,
            lines,
            aids(),
            EffectiveDate(2025, 3, 10),
        )

        assertEquals(EffectiveDate(2025, 3, 10), quote.effectiveDate)
    }

    @Test
    fun `a devis cannot predate the study it is built on`() {
        // Neither type can see the other's date, so nothing else would catch this. A devis dated
        // before its study would apply a bareme older than the method that produced the figures it
        // prices — indefensible to an auditor, and silently plausible on the page.
        val failure = assertFailsWith<IllegalArgumentException> {
            assembler.build(
                study(EffectiveDate(2025, 3, 10)),
                product,
                lines,
                aids(),
                EffectiveDate(2025, 3, 1),
            )
        }
        assertEquals(true, failure.message?.contains("cannot predate"))
    }
}
