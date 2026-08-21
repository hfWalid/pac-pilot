package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.DimensioningId
import fr.pacpilot.core.shared.ElectricalSupplyKva
import fr.pacpilot.core.shared.InstallerId
import fr.pacpilot.core.shared.InstantUtc
import fr.pacpilot.core.shared.PowerBand
import fr.pacpilot.core.shared.PowerKw
import fr.pacpilot.core.shared.SiteId
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DimensioningTest {

    private fun inputs(
        baseTemperature: TemperatureC = TemperatureC(-70),
        targetIndoorTemperature: TemperatureC = TemperatureC(190),
    ) = InputsSnapshot(
        surface = SurfaceM2.ofWholeSquareMetres(120),
        ceilingHeight = CeilingHeightM(250),
        constructionPeriod = ConstructionPeriod.BEFORE_1975,
        insulationLevel = InsulationLevel.PARTIAL,
        ventilationType = VentilationType.VMC_SIMPLE_FLUX,
        emitterType = EmitterType.RADIATOR_HIGH_TEMPERATURE,
        climateZone = ClimateZone.H1,
        baseTemperature = baseTemperature,
        targetIndoorTemperature = targetIndoorTemperature,
        availableElectricalPower = ElectricalSupplyKva(9),
    )

    private fun result() = HeatLoadResult(
        heatLoad = PowerKw(12_400),
        recommendedPowerBand = PowerBand(PowerKw(11_000), PowerKw(14_000)),
        recommendedFlowTemperature = TemperatureC(550),
        assumptions = AssumptionsLog(
            listOf(Assumption("U-value inferred from construction period", Assumption.SOURCE_TBD)),
        ),
    )

    private fun study() = Dimensioning.computed(
        id = DimensioningId("dim-1"),
        siteId = SiteId("site-1"),
        inputs = inputs(),
        result = result(),
    )

    @Test
    fun `a freshly computed study is unsigned`() {
        val study = study()
        assertNull(study.validation)
        assertTrue(!study.isValidated)
    }

    @Test
    fun `validating records who signed and when, without touching the computation`() {
        val computed = study()
        val validated = computed.validate(InstallerId("installer-1"), InstantUtc(1_760_000_000_000))

        assertTrue(validated.isValidated)
        assertEquals(InstallerId("installer-1"), validated.validation?.validatedBy)
        assertEquals(InstantUtc(1_760_000_000_000), validated.validation?.validatedAt)
        // The proposal is untouched — the legal shield rests on these being distinguishable.
        assertEquals(computed.result, validated.result)
        assertEquals(computed.inputs, validated.inputs)
    }

    @Test
    fun `validating leaves the original instance unsigned`() {
        val computed = study()
        computed.validate(InstallerId("installer-1"), InstantUtc.EPOCH)
        assertTrue(!computed.isValidated, "a stale reference must not observe a signature")
    }

    @Test
    fun `refuses a second signature on an already validated study`() {
        val validated = study().validate(InstallerId("installer-1"), InstantUtc(1_000))
        assertFailsWith<IllegalArgumentException> {
            validated.validate(InstallerId("installer-2"), InstantUtc(2_000))
        }
    }

    @Test
    fun `refuses inputs where the outdoor design temperature is not below the indoor target`() {
        // A base temperature at or above the target inverts the heat load's sign.
        assertFailsWith<IllegalArgumentException> {
            inputs(baseTemperature = TemperatureC(200), targetIndoorTemperature = TemperatureC(190))
        }
        assertFailsWith<IllegalArgumentException> {
            inputs(baseTemperature = TemperatureC(190), targetIndoorTemperature = TemperatureC(190))
        }
    }

    @Test
    fun `reports the result as provisional while any coefficient is unsourced`() {
        assertTrue(study().result.isProvisional)

        val sourced = HeatLoadResult(
            heatLoad = PowerKw(12_400),
            recommendedPowerBand = PowerBand(PowerKw(11_000), PowerKw(14_000)),
            recommendedFlowTemperature = null,
            assumptions = AssumptionsLog(listOf(Assumption("Air change rate", "EN 12831 6.3"))),
        )
        assertTrue(!sourced.isProvisional)
    }

    @Test
    fun `refuses a computed result that records no reasoning`() {
        // PRODUCT-VIEWS #5: the installer sees what was assumed before signing. A result with an
        // empty log means the engine applied defaults it did not disclose.
        assertFailsWith<IllegalArgumentException> {
            HeatLoadResult(
                heatLoad = PowerKw(12_400),
                recommendedPowerBand = PowerBand(PowerKw(11_000), PowerKw(14_000)),
                recommendedFlowTemperature = null,
                assumptions = AssumptionsLog(emptyList()),
            )
        }
    }

    @Test
    fun `confidence follows whether every coefficient cites a source`() {
        assertEquals(Confidence.INDICATIVE, study().result.confidence)

        val sourced = HeatLoadResult(
            heatLoad = PowerKw(12_400),
            recommendedPowerBand = PowerBand(PowerKw(11_000), PowerKw(14_000)),
            recommendedFlowTemperature = null,
            assumptions = AssumptionsLog(listOf(Assumption("Air change rate", "EN 12831 6.3"))),
        )
        assertEquals(Confidence.SUPPORTED, sourced.confidence)
    }

    @Test
    fun `an assumption must cite a source or say plainly it has none`() {
        assertFailsWith<IllegalArgumentException> { Assumption("something", "") }
        assertFailsWith<IllegalArgumentException> { Assumption("", Assumption.SOURCE_TBD) }
    }

    @Test
    fun `a power band runs upward and knows what it contains`() {
        val band = PowerBand(PowerKw(11_000), PowerKw(14_000))
        assertTrue(band.contains(PowerKw(11_000)))
        assertTrue(band.contains(PowerKw(14_000)))
        assertTrue(!band.contains(PowerKw(10_999)))
        assertFailsWith<IllegalArgumentException> { PowerBand(PowerKw(14_000), PowerKw(11_000)) }
    }
}
