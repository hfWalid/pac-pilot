package fr.pacpilot.core.vectors

import fr.pacpilot.core.CoreInfo
import fr.pacpilot.core.aids.engine.AidsEngine
import fr.pacpilot.core.aids.engine.InMemoryRulePackRepository
import fr.pacpilot.core.aids.engine.SampleAidRulePacks
import fr.pacpilot.core.aids.model.AidLine
import fr.pacpilot.core.aids.model.AidRuleId
import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.HeatPumpType
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.ReplacedSystem
import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.aids.model.ResteACharge
import fr.pacpilot.core.dimensioning.engine.DimensioningEngine
import fr.pacpilot.core.dimensioning.engine.ProvisionalFormulaSetProvider
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.InputsSnapshot
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.ElectricalSupplyKva
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.PowerKw
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs every golden vector on whichever target is executing — `:core:jvmTest` and `:core:jsTest`
 * both run this same class over the same fixtures. That is the whole mechanism: one set of
 * expectations, two runtimes, no way for them to drift apart unnoticed.
 *
 * Adding a vector means editing a `.vectors` file. No change to either platform's test wiring.
 */
class GoldenVectorSuite {

    private val vectors: List<GoldenVector> = GoldenVectorParser.parse(GoldenVectorsResource.CONTENT)

    @Test
    fun `every vector produces its expected output`() {
        assertTrue(vectors.isNotEmpty(), "No golden vectors found — the harness would pass vacuously")

        vectors.forEach { vector ->
            val actual = evaluate(vector)
            vector.expected.forEach { (key, expected) ->
                assertEquals(
                    expected,
                    actual[key],
                    "Vector '${vector.id}' (${vector.description}) — key '$key'",
                )
            }
        }
    }

    @Test
    fun `every vector declares a source or is explicitly marked provisional`() {
        // Guards CLAUDE.md §12: an unsourced number that looks authoritative is the failure mode
        // this project cannot afford. A vector must either cite where its value comes from or say
        // plainly that it does not yet know.
        vectors.forEach { vector ->
            assertTrue(
                vector.source.isNotBlank(),
                "Vector '${vector.id}' has no source; cite one or mark it ${GoldenVector.SOURCE_TBD}",
            )
        }
    }

    @Test
    fun `vector ids are unique`() {
        // Ids are the stable handle for an append-only corpus. A duplicate means one vector
        // silently shadows another in review and in failure messages.
        val duplicates = vectors.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Duplicate vector ids: $duplicates")
    }

    /**
     * Dispatches a vector to the engine entry point it names.
     *
     * Keeping dispatch explicit — rather than reflective — means an unknown operation fails loudly
     * instead of being silently skipped, which would let a vector look green while testing nothing.
     */
    private fun evaluate(vector: GoldenVector): Map<String, String> = when (vector.operation) {
        "core.identify" -> mapOf("value" to CoreInfo.identify())
        "shared.render" -> mapOf("render" to renderUnit(vector.inputs))
        "shared.applyPercentage" -> mapOf("render" to applyPercentage(vector.inputs))
        "date.render" -> mapOf("render" to renderDate(vector.inputs))
        "aids.resteACharge" -> resteACharge(vector.inputs)
        "aids.chain" -> aidsChain(vector.inputs)
        "aids.resolve" -> aidsResolve(vector.inputs)
        "dimensioning.heatLoad" -> heatLoad(vector.inputs)
        else -> error("Vector '${vector.id}' names unknown operation '${vector.operation}'")
    }

    /**
     * Renders one shared unit from its minor-unit integer (M1-01).
     *
     * These vectors pin the *rendering* rather than any domain value, because rendering is where the
     * two targets would have drifted apart invisibly: `Double.toString()` produces "1.0" on the JVM
     * and "1" in the browser. The fixed-point types exist to make that impossible, and this is where
     * the guarantee is actually exercised on both runtimes.
     */
    private fun renderUnit(inputs: Map<String, String>): String {
        val minorUnits = inputs.getValue("minorUnits")
        return when (val type = inputs.getValue("type")) {
            "MoneyEur" -> MoneyEur(minorUnits.toLong()).render()
            "Percentage" -> Percentage(minorUnits.toInt()).render()
            "PowerKw" -> PowerKw(minorUnits.toInt()).render()
            "TemperatureC" -> TemperatureC(minorUnits.toInt()).render()
            "SurfaceM2" -> SurfaceM2(minorUnits.toInt()).render()
            else -> error("Unknown shared type '$type'")
        }
    }

    /** Applies a rate to an amount, pinning the one rounding rule the domain is allowed to use. */
    private fun applyPercentage(inputs: Map<String, String>): String =
        Percentage(inputs.getValue("basisPoints").toInt())
            .applyTo(MoneyEur(inputs.getValue("cents").toLong()))
            .render()

    /** Pins the ISO rendering and the Gregorian leap rule that decide which rule pack applies. */
    private fun renderDate(inputs: Map<String, String>): String =
        EffectiveDate(
            inputs.getValue("year").toInt(),
            inputs.getValue("month").toInt(),
            inputs.getValue("day").toInt(),
        ).render()

    /**
     * Pins the figure the homeowner reads on-site, and the flag that fires instead of clamping it.
     *
     * The aid is a single line with a placeholder source: these vectors assert the *arithmetic*,
     * not a barème. Real MaPrimeRénov' and CEE figures arrive at M3 with citations attached.
     */
    private fun resteACharge(inputs: Map<String, String>): Map<String, String> {
        val aids = ResolvedAids(
            AidRulePackVersion("vector-pack"),
            listOf(AidLine(AidRuleId("vector-aid"), "Aide", MoneyEur(inputs.getValue("aidCents").toLong()), "SOURCE_TBD")),
        )
        val result = ResteACharge.of(MoneyEur(inputs.getValue("workCostCents").toLong()), aids)
        return mapOf(
            "render" to result.amount.render(),
            "overGranted" to result.isOverGranted.toString(),
        )
    }

    /**
     * A realistic aids chain end to end: TVA on the work cost, a percentage aid clipped by a cap,
     * and a fixed CEE amount, ending at the reste-a-charge.
     *
     * Every rate and cap in the fixture is illustrative — the real ones arrive at M3 with citations.
     * What this pins is that a five-figure job priced through four operations lands on the identical
     * cent on the installer's phone and on the server, which is the guarantee of CLAUDE.md 4.2.
     */
    private fun aidsChain(inputs: Map<String, String>): Map<String, String> {
        val workCost = MoneyEur(inputs.getValue("workCostCents").toLong())
        val tva = Percentage(inputs.getValue("tvaBasisPoints").toInt()).applyTo(workCost)
        val totalCost = workCost + tva

        val rateAid = Percentage(inputs.getValue("aidRateBasisPoints").toInt()).applyTo(workCost)
        val cap = MoneyEur(inputs.getValue("aidCapCents").toLong())
        val cappedAid = if (rateAid > cap) cap else rateAid

        val aids = ResolvedAids(
            AidRulePackVersion("vector-pack"),
            listOf(
                AidLine(AidRuleId("taux-plafonne"), "Aide au taux plafonne", cappedAid, "SOURCE_TBD"),
                AidLine(
                    AidRuleId("cee-forfait"),
                    "CEE forfaitaire",
                    MoneyEur(inputs.getValue("ceeCents").toLong()),
                    "SOURCE_TBD",
                ),
            ),
        )

        return mapOf(
            "tva" to tva.render(),
            "totalAids" to aids.total.render(),
            "resteACharge" to ResteACharge.of(totalCost, aids).amount.render(),
        )
    }

    /**
     * Runs the M3 evaluator over the two sample packs — the vectors that bind *pack resolution*
     * across both targets, not just arithmetic over hand-built aids.
     *
     * Heat-pump type, climate zone and replaced system are fixed rather than varied: no rule in the
     * sample pack conditions on them, so varying them would produce vectors differing only in
     * fields nothing reads. They become interesting at M6, when a real CEE fiche conditions on the
     * replaced system.
     *
     * A rule that produced no line renders as `absent` rather than being omitted from the map. An
     * omitted key would compare equal to nothing at all, so a vector asserting a missing line would
     * pass whether the line was missing or the key was misspelt.
     */
    private fun aidsResolve(inputs: Map<String, String>): Map<String, String> {
        val aidsInputs = AidsInputs(
            incomeDecile = IncomeDecile(inputs.getValue("incomeDecile").toInt()),
            heatPumpType = HeatPumpType.AIR_WATER,
            climateZone = ClimateZone.H1,
            replacedSystem = ReplacedSystem.OIL_BOILER,
            workCost = MoneyEur(inputs.getValue("workCostCents").toLong()),
        )
        val effectiveDate = EffectiveDate(
            inputs.getValue("year").toInt(),
            inputs.getValue("month").toInt(),
            inputs.getValue("day").toInt(),
        )

        val engine = AidsEngine(InMemoryRulePackRepository.withSamplePacks())
        return when (val outcome = engine.resolve(aidsInputs, effectiveDate)) {
            is AidsOutcome.NoPackPublished -> mapOf("outcome" to "NoPackPublished")
            is AidsOutcome.Resolved -> {
                val resolution = outcome.resolution
                fun amountOf(rule: AidRuleId): String =
                    resolution.aids.lines.firstOrNull { it.rule == rule }?.amount?.render() ?: "absent"

                mapOf(
                    "outcome" to "Resolved",
                    "packVersion" to resolution.aids.packVersion.value,
                    "tiered" to amountOf(SampleAidRulePacks.INCOME_TIERED),
                    "forfait" to amountOf(SampleAidRulePacks.FORFAIT),
                    "rateBased" to amountOf(SampleAidRulePacks.RATE_BASED),
                    "totalAids" to resolution.aids.total.render(),
                    "totalIncludingVat" to resolution.estimatedTotalIncludingVat.render(),
                    "resteACharge" to resolution.estimatedResteACharge.amount.render(),
                    "overGranted" to resolution.estimatedResteACharge.isOverGranted.toString(),
                )
            }
        }
    }

    /**
     * Runs the M2 engine over the provisional formula set — the first vectors that bind a domain
     * *calculation* across both targets rather than a rendering.
     *
     * Climate zone and electrical supply are fixed rather than varied: the engine reads neither. The
     * zone selects a base temperature, which M1-04 resolves at the boundary into the snapshot, and
     * the supply constrains machine selection at M4 rather than the heat load. Varying them here
     * would produce vectors that differ only in fields nothing reads.
     */
    private fun heatLoad(inputs: Map<String, String>): Map<String, String> {
        val snapshot = InputsSnapshot(
            surface = SurfaceM2(inputs.getValue("surfaceCentiM2").toInt()),
            ceilingHeight = CeilingHeightM(inputs.getValue("ceilingHeightCm").toInt()),
            constructionPeriod = ConstructionPeriod.valueOf(inputs.getValue("constructionPeriod")),
            insulationLevel = InsulationLevel.valueOf(inputs.getValue("insulationLevel")),
            ventilationType = VentilationType.valueOf(inputs.getValue("ventilationType")),
            emitterType = EmitterType.valueOf(inputs.getValue("emitterType")),
            climateZone = ClimateZone.H1,
            baseTemperature = TemperatureC(inputs.getValue("baseTemperatureDeciC").toInt()),
            targetIndoorTemperature =
                TemperatureC(inputs.getValue("targetIndoorTemperatureDeciC").toInt()),
            availableElectricalPower = ElectricalSupplyKva(9),
        )

        val engine = DimensioningEngine(ProvisionalFormulaSetProvider())
        return when (val outcome = engine.run(snapshot, EffectiveDate(2026, 8, 22))) {
            is DimensioningOutcome.Computed -> mapOf(
                "outcome" to "Computed",
                "heatLoad" to outcome.result.heatLoad.render(),
                "bandMinimum" to outcome.result.recommendedPowerBand.minimum.render(),
                "bandMaximum" to outcome.result.recommendedPowerBand.maximum.render(),
                "flowTemperature" to
                    (outcome.result.recommendedFlowTemperature?.render() ?: "withheld"),
                "confidence" to outcome.result.confidence.name,
            )
            is DimensioningOutcome.ManualStudyRequired -> mapOf(
                "outcome" to "ManualStudyRequired",
                "reasons" to outcome.reasons.joinToString(",") { it.name },
            )
        }
    }
}
