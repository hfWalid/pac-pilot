package fr.pacpilot.core.vectors

import fr.pacpilot.core.CoreInfo
import fr.pacpilot.core.aids.model.AidLine
import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.aids.model.ResteACharge
import fr.pacpilot.core.shared.EffectiveDate
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
     * M2 and M3 extend this with `dimensioning.heatLoad` and `aids.resolve`. Keeping dispatch
     * explicit — rather than reflective — means an unknown operation fails loudly instead of being
     * silently skipped, which would let a vector look green while testing nothing.
     */
    private fun evaluate(vector: GoldenVector): Map<String, String> = when (vector.operation) {
        "core.identify" -> mapOf("value" to CoreInfo.identify())
        "shared.render" -> mapOf("render" to renderUnit(vector.inputs))
        "shared.applyPercentage" -> mapOf("render" to applyPercentage(vector.inputs))
        "date.render" -> mapOf("render" to renderDate(vector.inputs))
        "aids.resteACharge" -> resteACharge(vector.inputs)
        "aids.chain" -> aidsChain(vector.inputs)
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
            listOf(AidLine("Aide", MoneyEur(inputs.getValue("aidCents").toLong()), "SOURCE_TBD")),
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
                AidLine("Aide au taux plafonne", cappedAid, "SOURCE_TBD"),
                AidLine("CEE forfaitaire", MoneyEur(inputs.getValue("ceeCents").toLong()), "SOURCE_TBD"),
            ),
        )

        return mapOf(
            "tva" to tva.render(),
            "totalAids" to aids.total.render(),
            "resteACharge" to ResteACharge.of(totalCost, aids).amount.render(),
        )
    }
}
