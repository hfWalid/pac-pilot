package fr.pacpilot.core.dimensioning.engine

import fr.pacpilot.core.dimensioning.model.Confidence
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.InputsSnapshot
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.RefusalReason
import fr.pacpilot.core.dimensioning.model.ValidatedEnvelope
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.dimensioning.port.FlowTemperatureGuidance
import fr.pacpilot.core.dimensioning.port.FormulaSet
import fr.pacpilot.core.dimensioning.port.FormulaSetProvider
import fr.pacpilot.core.dimensioning.port.Sourced
import fr.pacpilot.core.shared.AirChangeRate
import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.ElectricalSupplyKva
import fr.pacpilot.core.shared.EnvelopeAreaFactor
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC
import fr.pacpilot.core.shared.ThermalTransmittance
import fr.pacpilot.core.shared.VolumetricHeatCapacity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ANY_DATE = EffectiveDate(2026, 8, 22)

/** Records which accessors the engine actually reached for, so the log can be checked against it. */
private class SpyFormulaSet(
    private val delegate: FormulaSet = ProvisionalFormulaSet(),
    private val everythingSourced: Boolean = false,
) : FormulaSet {

    val reads = mutableListOf<String>()

    private fun <T> resource(name: String, sourced: Sourced<T>): Sourced<T> {
        reads += name
        return if (everythingSourced) Sourced(sourced.value, "EN 12831 (test double)") else sourced
    }

    override val envelope: ValidatedEnvelope get() = delegate.envelope

    override fun uValueFor(p: ConstructionPeriod, i: InsulationLevel) =
        resource("uValue", delegate.uValueFor(p, i))

    override fun airChangeRateFor(v: VentilationType) =
        resource("airChangeRate", delegate.airChangeRateFor(v))

    override fun airVolumetricHeatCapacity() =
        resource("airVolumetricHeatCapacity", delegate.airVolumetricHeatCapacity())

    override fun envelopeAreaFactor() =
        resource("envelopeAreaFactor", delegate.envelopeAreaFactor())

    override fun underSizingMargin() = resource("underSizingMargin", delegate.underSizingMargin())

    override fun overSizingMargin() = resource("overSizingMargin", delegate.overSizingMargin())

    override fun flowTemperatureFor(emitter: EmitterType): FlowTemperatureGuidance {
        reads += "flowTemperature"
        val guidance = delegate.flowTemperatureFor(emitter)
        if (!everythingSourced) return guidance
        return when (guidance) {
            is FlowTemperatureGuidance.Advised ->
                FlowTemperatureGuidance.Advised(guidance.flowTemperature, "EN 12831 (test double)")
            is FlowTemperatureGuidance.Withheld ->
                FlowTemperatureGuidance.Withheld("EN 12831 (test double)")
        }
    }
}

/** Fails on any coefficient access — proves the envelope short-circuit is real, not ordering luck. */
private class HostileFormulaSet(private val envelopeToUse: ValidatedEnvelope) : FormulaSet {
    override val envelope: ValidatedEnvelope get() = envelopeToUse
    private fun refuse(): Nothing = error("the engine read a coefficient for an out-of-envelope dwelling")
    override fun uValueFor(p: ConstructionPeriod, i: InsulationLevel) = refuse()
    override fun airChangeRateFor(v: VentilationType) = refuse()
    override fun airVolumetricHeatCapacity() = refuse()
    override fun envelopeAreaFactor() = refuse()
    override fun underSizingMargin() = refuse()
    override fun overSizingMargin() = refuse()
    override fun flowTemperatureFor(emitter: EmitterType) = refuse()
}

private fun provider(formulaSet: FormulaSet) = object : FormulaSetProvider {
    override fun formulaSetOn(effectiveDate: EffectiveDate) = formulaSet
}

class DimensioningEngineTest {

    private fun inputs(
        surface: SurfaceM2 = SurfaceM2.ofWholeSquareMetres(120),
        ceilingHeight: CeilingHeightM = CeilingHeightM(250),
        emitter: EmitterType = EmitterType.RADIATOR_HIGH_TEMPERATURE,
        baseTemperature: TemperatureC = TemperatureC(-70),
    ) = InputsSnapshot(
        surface = surface,
        ceilingHeight = ceilingHeight,
        constructionPeriod = ConstructionPeriod.BEFORE_1975,
        insulationLevel = InsulationLevel.PARTIAL,
        ventilationType = VentilationType.VMC_SIMPLE_FLUX,
        emitterType = emitter,
        climateZone = ClimateZone.H1,
        baseTemperature = baseTemperature,
        targetIndoorTemperature = TemperatureC(190),
        availableElectricalPower = ElectricalSupplyKva(9),
    )

    private fun run(formulaSet: FormulaSet, snapshot: InputsSnapshot = inputs()) =
        DimensioningEngine(provider(formulaSet)).run(snapshot, ANY_DATE)

    // ---- M2-03: the calculation and its injection ----

    @Test
    fun `computes a heat load for a dwelling inside the envelope`() {
        val outcome = run(ProvisionalFormulaSet())
        assertTrue(outcome is DimensioningOutcome.Computed)
        assertEquals("19.032", outcome.result.heatLoad.render())
    }

    @Test
    fun `the formula set is genuinely injected, not decorative`() {
        // Two different sets over identical inputs must disagree. If they agree, something is
        // hardcoded and the whole gate-late strategy is an illusion.
        val doubled = object : FormulaSet by ProvisionalFormulaSet() {
            override fun envelopeAreaFactor() =
                Sourced(EnvelopeAreaFactor(2_000), "SOURCE_TBD (test double)")
        }
        val a = run(ProvisionalFormulaSet()) as DimensioningOutcome.Computed
        val b = run(doubled) as DimensioningOutcome.Computed
        assertTrue(a.result.heatLoad != b.result.heatLoad, "changing a coefficient changed nothing")
    }

    // ---- M2-02: the envelope ----

    @Test
    fun `a dwelling below the surface range is refused, not estimated`() {
        val outcome = run(ProvisionalFormulaSet(), inputs(surface = SurfaceM2.ofWholeSquareMetres(10)))
        assertTrue(outcome is DimensioningOutcome.ManualStudyRequired)
        assertEquals(listOf(RefusalReason.SURFACE_OUTSIDE_RANGE), outcome.reasons)
    }

    @Test
    fun `every breach is reported, not just the first`() {
        val outcome = run(
            ProvisionalFormulaSet(),
            inputs(
                surface = SurfaceM2.ofWholeSquareMetres(500),
                ceilingHeight = CeilingHeightM(400),
                baseTemperature = TemperatureC(-300),
            ),
        )
        assertTrue(outcome is DimensioningOutcome.ManualStudyRequired)
        assertEquals(
            listOf(
                RefusalReason.SURFACE_OUTSIDE_RANGE,
                RefusalReason.CEILING_HEIGHT_OUTSIDE_RANGE,
                RefusalReason.BASE_TEMPERATURE_OUTSIDE_RANGE,
            ),
            outcome.reasons,
        )
    }

    @Test
    fun `a refusal reads no coefficient at all`() {
        val hostile = HostileFormulaSet(ProvisionalFormulaSet().envelope)
        val outcome = run(hostile, inputs(surface = SurfaceM2.ofWholeSquareMetres(10)))
        assertTrue(outcome is DimensioningOutcome.ManualStudyRequired)
    }

    // ---- M2-04: the power band ----

    @Test
    fun `the band always contains the heat load it came from`() {
        val outcome = run(ProvisionalFormulaSet()) as DimensioningOutcome.Computed
        assertTrue(outcome.result.recommendedPowerBand.contains(outcome.result.heatLoad))
    }

    @Test
    fun `the two margins move their own end of the band and nothing else`() {
        val base = run(ProvisionalFormulaSet()) as DimensioningOutcome.Computed
        val widerUnder = object : FormulaSet by ProvisionalFormulaSet() {
            override fun underSizingMargin() = Sourced(Percentage(3_000), "SOURCE_TBD (test double)")
        }
        val moved = run(widerUnder) as DimensioningOutcome.Computed

        assertTrue(
            moved.result.recommendedPowerBand.minimum < base.result.recommendedPowerBand.minimum,
            "the under margin did not move the lower bound",
        )
        assertEquals(
            base.result.recommendedPowerBand.maximum,
            moved.result.recommendedPowerBand.maximum,
            "the under margin moved the upper bound; the two are wired together",
        )
    }

    // ---- M2-05: loi d'eau ----

    @Test
    fun `every emitter type yields a sourced temperature or an explicit absence`() {
        EmitterType.entries.forEach { emitter ->
            val outcome = run(ProvisionalFormulaSet(), inputs(emitter = emitter)) as
                DimensioningOutcome.Computed
            val guidance = outcome.result.recommendedFlowTemperature
            val logged = outcome.result.assumptions.entries.any { it.statement.contains("Loi d'eau") }
            assertTrue(logged, "$emitter did not record its loi d'eau finding")
            if (emitter == EmitterType.FAN_COIL) assertNull(guidance) else assertTrue(guidance != null)
        }
    }

    @Test
    fun `withholding guidance does not block the study`() {
        // An in-envelope dwelling whose emitters the method cannot advise on still gets a heat load.
        // A withheld guidance is not an out-of-envelope refusal.
        val outcome = run(ProvisionalFormulaSet(), inputs(emitter = EmitterType.FAN_COIL))
        assertTrue(outcome is DimensioningOutcome.Computed)
        assertEquals("19.032", outcome.result.heatLoad.render())
        assertNull(outcome.result.recommendedFlowTemperature)
        assertTrue(
            outcome.result.assumptions.entries.any { it.statement.contains("aucune preconisation") },
            "the log must say the method declined, not stay silent",
        )
    }

    // ---- M2-06: the assumptions log ----

    @Test
    fun `the log records exactly the coefficients the engine read`() {
        // The decisive property. A coefficient read but unlogged is the drift this design prevents.
        val spy = SpyFormulaSet()
        val outcome = run(spy) as DimensioningOutcome.Computed
        assertEquals(
            spy.reads.size,
            outcome.result.assumptions.entries.size,
            "logged ${outcome.result.assumptions.entries.size} assumptions for ${spy.reads.size} reads",
        )
        assertTrue(spy.reads.isNotEmpty())
    }

    @Test
    fun `confidence follows the sources the formula set supplied`() {
        val provisional = run(SpyFormulaSet()) as DimensioningOutcome.Computed
        assertEquals(Confidence.INDICATIVE, provisional.result.confidence)

        val sourced = run(SpyFormulaSet(everythingSourced = true)) as DimensioningOutcome.Computed
        assertEquals(Confidence.SUPPORTED, sourced.result.confidence)
    }

    // ---- M2-07: the provisional set ----

    @Test
    fun `every coefficient in the provisional set is marked provisional`() {
        val outcome = run(ProvisionalFormulaSet()) as DimensioningOutcome.Computed
        assertTrue(outcome.result.isProvisional)
        outcome.result.assumptions.entries.forEach {
            assertTrue(it.isProvisional, "a coefficient escaped without SOURCE_TBD: ${it.statement}")
        }
    }
}
