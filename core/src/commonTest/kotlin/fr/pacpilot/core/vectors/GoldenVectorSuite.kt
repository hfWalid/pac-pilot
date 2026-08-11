package fr.pacpilot.core.vectors

import fr.pacpilot.core.CoreInfo
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
        else -> error("Vector '${vector.id}' names unknown operation '${vector.operation}'")
    }
}
