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

    private fun study(): ComputedDimensioning = Dimensioning.computed(
        id = DimensioningId("dim-1"),
        siteId = SiteId("site-1"),
        inputs = inputs(),
        outcome = DimensioningOutcome.Computed(result()),
    )

    @Test
    fun `a freshly computed study has no signature to read`() {
        // Not "validation is null" — a ComputedDimensioning has no validation member at all, so a
        // caller cannot read one by mistake. That is the difference between the sealed hierarchy
        // and a nullable field.
        val computed: Dimensioning = study()
        assertTrue(computed is ComputedDimensioning)
    }

    @Test
    fun `validating yields a different type carrying who signed and when`() {
        val computed = study()
        val validated: ValidatedDimensioning =
            computed.validate(InstallerId("installer-1"), InstantUtc(1_760_000_000_000))

        assertEquals(InstallerId("installer-1"), validated.validation.validatedBy)
        assertEquals(InstantUtc(1_760_000_000_000), validated.validation.validatedAt)
        // The proposal is untouched: the legal shield rests on computed and signed being
        // distinguishable after the fact, not on one overwriting the other.
        assertEquals(computed.result, validated.result)
        assertEquals(computed.inputs, validated.inputs)
        assertEquals(computed.id, validated.id)
    }

    @Test
    fun `the computed instance stays unsigned after validation`() {
        val computed = study()
        computed.validate(InstallerId("installer-1"), InstantUtc.EPOCH)
        assertTrue(computed is ComputedDimensioning, "a stale reference must not observe a signature")
    }

    @Test
    fun `a signed study cannot be re-signed or have its inputs swapped`() {
        // Both are compile-time guarantees rather than runtime checks, which is what the ticket
        // asks for: ValidatedDimensioning has no validate(), and neither case is a data class, so
        // there is no copy() to move a signature onto different inputs. What is assertable here is
        // that the signature and the calculation it covers are the ones that were signed.
        val validated = study().validate(InstallerId("installer-1"), InstantUtc(1_000))
        assertEquals(inputs(), validated.inputs)
        assertEquals(InstallerId("installer-1"), validated.validation.validatedBy)
    }

    @Test
    fun `a refusal cannot become a study at all`() {
        // Dimensioning.computed accepts only DimensioningOutcome.Computed, so passing a refusal
        // does not compile. The runtime half: a refusal exposes no result to smuggle in.
        val refusal = DimensioningOutcome.ManualStudyRequired(
            listOf(RefusalReason.SURFACE_OUTSIDE_RANGE),
        )
        val rendered = when (refusal as DimensioningOutcome) {
            is DimensioningOutcome.Computed -> "computed"
            is DimensioningOutcome.ManualStudyRequired -> "etude manuelle requise"
        }
        assertEquals("etude manuelle requise", rendered)
    }

    @Test
    fun `aggregates compare by identity`() {
        val other = Dimensioning.computed(
            id = DimensioningId("dim-1"),
            siteId = SiteId("site-2"),
            inputs = inputs(),
            outcome = DimensioningOutcome.Computed(result()),
        )
        assertEquals(study(), other)
        assertEquals(study().hashCode(), other.hashCode())
    }

    @Test
    fun `refuses inputs where the outdoor design temperature is not below the indoor target`() {
        assertFailsWith<IllegalArgumentException> {
            inputs(baseTemperature = TemperatureC(200), targetIndoorTemperature = TemperatureC(190))
        }
        assertFailsWith<IllegalArgumentException> {
            inputs(baseTemperature = TemperatureC(190), targetIndoorTemperature = TemperatureC(190))
        }
    }

    @Test
    fun `refuses a computed result that records no reasoning`() {
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
