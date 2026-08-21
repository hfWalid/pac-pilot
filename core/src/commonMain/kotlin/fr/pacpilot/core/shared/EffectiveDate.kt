package fr.pacpilot.core.shared

/**
 * A calendar date with no time and no zone — the date a devis takes effect.
 *
 * This is the type the whole reproducibility guarantee turns on. A rule pack is resolved by the
 * date on the devis (CLAUDE.md §4.4, §7), so a devis written today still recomputes against the
 * barème that was in force when it was written, years later. The date is an **input**, never read
 * from a clock: `ArchitecturePurityTest` bans `java.time` and `kotlin.time.Clock` from the core
 * precisely so that this stays true, and this type is the reason that rule can now be narrowed
 * rather than deleted.
 *
 * Hand-rolled rather than borrowed: `java.time.LocalDate` is JVM-only and would not compile to JS,
 * and `kotlinx-datetime` is deliberately not a dependency. The arithmetic below is small, total,
 * and identical on both targets.
 *
 * **Java surface (ADR-0010).** `new EffectiveDate(2026, 8, 21)` from `:server`; `Comparable` so
 * ordering works with `compareTo` and Java's own sorts.
 */
data class EffectiveDate(val year: Int, val month: Int, val day: Int) : Comparable<EffectiveDate> {

    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        val lengthOfMonth = lengthOfMonth(year, month)
        require(day in 1..lengthOfMonth) {
            "day must be 1..$lengthOfMonth for year $year month $month, was $day"
        }
    }

    /** ISO-8601, always ten characters: `2026-08-21`. Zero-padded so dates sort as text too. */
    fun render(): String = pad(year, 4) + "-" + pad(month, 2) + "-" + pad(day, 2)

    override fun compareTo(other: EffectiveDate): Int {
        val byYear = year.compareTo(other.year)
        if (byYear != 0) return byYear
        val byMonth = month.compareTo(other.month)
        if (byMonth != 0) return byMonth
        return day.compareTo(other.day)
    }

    override fun toString(): String = render()

    companion object {
        private const val FEBRUARY: Int = 2

        private val DAYS_PER_MONTH: List<Int> =
            listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        /** Proleptic Gregorian: divisible by 4, except centuries that are not divisible by 400. */
        fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun lengthOfMonth(year: Int, month: Int): Int {
            require(month in 1..12) { "month must be 1..12, was $month" }
            if (month == FEBRUARY && isLeapYear(year)) return 29
            return DAYS_PER_MONTH[month - 1]
        }

        private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')
    }
}
