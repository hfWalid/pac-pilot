package fr.pacpilot.core.shared

/**
 * A point in time in UTC, held as milliseconds since the Unix epoch.
 *
 * Exists for one reason: the liability record (CLAUDE.md §4.5) has to say *when* the artisan
 * validated a result, and that is a moment, not a calendar day — [EffectiveDate] is the wrong type
 * for it.
 *
 * **The core never reads a clock** (§10, enforced by `ArchitecturePurityTest`). An instant is always
 * supplied by the adapter that observed it. `java.time` is JVM-only and `kotlinx-datetime` is not on
 * the classpath, so a `Long` is also the only representation available in `commonMain` — the
 * constraint and the correct design happen to agree here.
 *
 * **No formatting.** Turning this into text is a presentation concern belonging to the PDF and REST
 * adapters, which know the reader's locale and timezone. The domain compares instants; it never
 * renders them. That is why this type has no `render()` while every unit in `shared` has one.
 */
data class InstantUtc(val epochMilliseconds: Long) : Comparable<InstantUtc> {

    override fun compareTo(other: InstantUtc): Int =
        epochMilliseconds.compareTo(other.epochMilliseconds)

    companion object {
        val EPOCH: InstantUtc = InstantUtc(0)
    }
}
