package fr.pacpilot.core.vectors

/**
 * One immutable input→output fixture. Together the vectors are the correctness contract binding the
 * JVM and JS targets (CLAUDE.md §4.2, ARCHITECTURE #3): both must produce identical results, so a
 * formula that behaves differently on the installer's phone than on the server fails in CI rather
 * than surfacing as a divergence flag in front of a homeowner.
 */
data class GoldenVector(
    val id: String,
    val description: String,
    /** A citation, or `SOURCE_TBD` while the coefficient is unvalidated. Never blank. */
    val source: String,
    /** Which engine entry point this vector exercises. */
    val operation: String,
    val inputs: Map<String, String>,
    val expected: Map<String, String>,
) {
    val isProvisional: Boolean get() = source.startsWith(SOURCE_TBD)

    companion object {
        const val SOURCE_TBD: String = "SOURCE_TBD"
    }
}

/**
 * Parses the `.vectors` format. Deliberately tiny and dependency-free: it runs inside `commonTest`
 * on both targets, and a vector file must stay readable by someone checking a barème against an
 * official source, not just by a machine.
 */
object GoldenVectorParser {

    fun parse(content: String): List<GoldenVector> =
        content.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .fold(mutableListOf<MutableMap<String, String>>()) { blocks, line ->
                if (line == "[vector]") {
                    blocks.add(mutableMapOf())
                } else {
                    val current = blocks.lastOrNull()
                        ?: error("Found '$line' before any [vector] header")
                    val key = line.substringBefore('=').trim()
                    val value = line.substringAfter('=', missingDelimiterValue = "").trim()
                    require(key.isNotEmpty() && line.contains('=')) { "Malformed line: '$line'" }
                    require(current.put(key, value) == null) { "Duplicate key '$key' in a vector" }
                }
                blocks
            }
            .map { it.toVector() }

    private fun Map<String, String>.toVector(): GoldenVector {
        fun required(key: String): String =
            this[key]?.takeIf { it.isNotBlank() }
                ?: error("Vector ${this["id"] ?: "<no id>"} is missing required key '$key'")

        return GoldenVector(
            id = required("id"),
            description = required("description"),
            source = required("source"),
            operation = required("operation"),
            inputs = filterKeys { it.startsWith("input.") }.mapKeys { it.key.removePrefix("input.") },
            expected = filterKeys { it.startsWith("expect.") }.mapKeys { it.key.removePrefix("expect.") },
        )
    }
}
