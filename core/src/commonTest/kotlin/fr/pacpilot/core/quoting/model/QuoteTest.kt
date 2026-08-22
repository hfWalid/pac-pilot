package fr.pacpilot.core.quoting.model

import fr.pacpilot.core.aids.model.AidLine
import fr.pacpilot.core.aids.model.AidRuleId
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
import kotlin.test.assertTrue

class QuoteTest {

    private val packVersion = AidRulePackVersion("2026-H1")
    private val reducedVat = Percentage(550)

    private fun validatedStudy(): ValidatedDimensioning = Dimensioning.computed(
        id = DimensioningId("dim-1"),
        siteId = SiteId("site-1"),
        inputs = InputsSnapshot(
            surface = SurfaceM2.ofWholeSquareMetres(120),
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
                heatLoad = PowerKw(12_400),
                recommendedPowerBand = PowerBand(PowerKw(11_000), PowerKw(14_000)),
                recommendedFlowTemperature = null,
                assumptions = AssumptionsLog(listOf(Assumption("U-value", Assumption.SOURCE_TBD))),
            ),
        ),
        effectiveDate = EffectiveDate(2026, 8, 21),
    ).validate(InstallerId("installer-1"), InstantUtc(1_000))

    private val product = ProductSnapshot(
        id = ProductId("product-1"),
        model = "Aquarea T-CAP 12 kW",
        powerAtMinusSevenC = PowerKw(12_000),
        priceAtQuoteTime = MoneyEur.ofEuros(9_500),
    )

    private fun quote(
        lines: List<LineItem> = listOf(
            LineItem("PAC air-eau 12 kW", MoneyEur.ofEuros(9_500), 1, reducedVat),
            LineItem("Pose et mise en service", MoneyEur.ofEuros(2_500), 1, reducedVat),
            LineItem("Radiateur basse temperature", MoneyEur.ofEuros(500), 4, reducedVat),
        ),
    ) = Quote.draft(
        id = QuoteId("quote-1"),
        dimensioning = validatedStudy(),
        product = product,
        lines = lines,
        resolvedAids = ResolvedAids(
            packVersion,
            listOf(AidLine(AidRuleId("mpr"), "MaPrimeRenov", MoneyEur.ofEuros(4_000), "anah.gouv.fr")),
        ),
        effectiveDate = EffectiveDate(2026, 8, 21),
    )

    @Test
    fun `derives every total from its lines`() {
        // 9 500 + 2 500 + (500 x 4) = 14 000 HT; TVA 5,5 % = 770,00; TTC = 14 770,00.
        val quote = quote()
        assertEquals("14000.00", quote.subtotalExcludingVat.render())
        assertEquals("770.00", quote.vat.render())
        assertEquals("14770.00", quote.totalIncludingVat.render())
    }

    @Test
    fun `a line item carries its own VAT rate`() {
        // TVA 5,5 % applies conditionally, so the rate is per line, not per devis.
        val line = LineItem("Radiateur", MoneyEur.ofEuros(500), 4, reducedVat)
        assertEquals("2000.00", line.total.render())
        assertEquals("110.00", line.vat.render())
        assertEquals("2110.00", line.totalIncludingVat.render())

        val standardRate = LineItem("Hors perimetre", MoneyEur.ofEuros(100), 1, Percentage(2_000))
        assertEquals("20.00", standardRate.vat.render())
    }

    @Test
    fun `reste a charge is the TTC total less the aids, and carries its pack version`() {
        val resteACharge = quote().resteACharge
        assertEquals("10770.00", resteACharge.amount.render())
        assertEquals(packVersion, resteACharge.packVersion)
        assertTrue(!resteACharge.isOverGranted)
    }

    @Test
    fun `everything needed to recompute the devis is reachable from it`() {
        // The reproducibility property: the bareme version and the dimensioning inputs.
        val quote = quote()
        assertEquals(packVersion, quote.resolvedAids.packVersion)
        assertEquals("2026-08-21", quote.effectiveDate.render())
        assertEquals(SurfaceM2.ofWholeSquareMetres(120), quote.dimensioning.inputs.surface)
        assertEquals(InstallerId("installer-1"), quote.dimensioning.validation.validatedBy)
    }

    @Test
    fun `the product attributes are snapshotted, so a catalogue change cannot rewrite the devis`() {
        val quote = quote()
        assertEquals("Aquarea T-CAP 12 kW", quote.product.model)
        assertEquals(PowerKw(12_000), quote.product.powerAtMinusSevenC)
        assertEquals("9500.00", quote.product.priceAtQuoteTime.render())
        // The catalogue entry moving on is a new ProductSnapshot for a new devis; this one holds
        // its own copy and there is no path from here back to the live catalogue.
        assertEquals(ProductId("product-1"), quote.product.id)
    }

    @Test
    fun `follows the ARCHITECTURE 7 happy path to accepted`() {
        val accepted = quote()
            .transitionTo(QuoteStatus.QUOTED)
            .transitionTo(QuoteStatus.AIDS_RESOLVED)
            .transitionTo(QuoteStatus.SENT)
            .transitionTo(QuoteStatus.ACCEPTED)
        assertEquals(QuoteStatus.ACCEPTED, accepted.status)
    }

    @Test
    fun `refuses a rejected devis being marked accepted`() {
        val rejected = quote()
            .transitionTo(QuoteStatus.QUOTED)
            .transitionTo(QuoteStatus.AIDS_RESOLVED)
            .transitionTo(QuoteStatus.SENT)
            .transitionTo(QuoteStatus.REJECTED)
        assertFailsWith<IllegalArgumentException> { rejected.transitionTo(QuoteStatus.ACCEPTED) }
    }

    @Test
    fun `refuses skipping a state`() {
        assertFailsWith<IllegalArgumentException> { quote().transitionTo(QuoteStatus.SENT) }
        assertFailsWith<IllegalArgumentException> { quote().transitionTo(QuoteStatus.ACCEPTED) }
    }

    @Test
    fun `accepted and rejected are terminal`() {
        assertTrue(QuoteStatus.ACCEPTED.allowedNext.isEmpty())
        assertTrue(QuoteStatus.REJECTED.allowedNext.isEmpty())
    }

    @Test
    fun `a transition returns a new devis and leaves the previous one alone`() {
        val draft = quote()
        val quoted = draft.transitionTo(QuoteStatus.QUOTED)
        assertEquals(QuoteStatus.DRAFT, draft.status)
        assertEquals(QuoteStatus.QUOTED, quoted.status)
    }

    @Test
    fun `refuses a devis with no lines`() {
        assertFailsWith<IllegalArgumentException> { quote(lines = emptyList()) }
    }

    @Test
    fun `refuses a line item with a non-positive quantity or no description`() {
        assertFailsWith<IllegalArgumentException> {
            LineItem("Pose", MoneyEur.ofEuros(100), 0, reducedVat)
        }
        assertFailsWith<IllegalArgumentException> {
            LineItem(" ", MoneyEur.ofEuros(100), 1, reducedVat)
        }
    }
}
