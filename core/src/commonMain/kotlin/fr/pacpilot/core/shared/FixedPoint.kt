package fr.pacpilot.core.shared

/**
 * Renders a fixed-point integer as a canonical decimal string, identically on every target.
 *
 * **Why this exists at all.** The golden-vector harness compares *strings* (`GoldenVectorSuite`),
 * and the keystone guarantee is that the JVM and JS targets never disagree (ARCHITECTURE #3).
 * `Double.toString()` breaks that guarantee outright: `1.0` renders as `"1.0"` on the JVM and as
 * `"1"` in the browser, and larger or smaller magnitudes diverge further. A divergence there would
 * not fail loudly — it would surface as an anomaly flag in front of a homeowner (CLAUDE.md §4.2).
 *
 * So the shared unit types store integers in a named minor unit and render through this function,
 * which is pure integer and string work: no floating point, no locale, no platform number
 * formatting. Same digits everywhere, forever.
 *
 * Works on the decimal text of [scaled] rather than on its arithmetic, because negating
 * [Long.MIN_VALUE] overflows and there is no reason to carry that edge case around.
 */
internal fun renderScaled(scaled: Long, decimals: Int): String {
    require(decimals >= 0) { "decimals must not be negative, was $decimals" }
    if (decimals == 0) return scaled.toString()

    val text = scaled.toString()
    val negative = text.startsWith('-')
    val magnitude = if (negative) text.substring(1) else text
    val padded = magnitude.padStart(decimals + 1, '0')
    val whole = padded.substring(0, padded.length - decimals)
    val fraction = padded.substring(padded.length - decimals)

    return buildString {
        if (negative) append('-')
        append(whole)
        append('.')
        append(fraction)
    }
}

/**
 * Divides [numerator] by [divisor], rounding halves away from zero.
 *
 * The single place a fractional minor unit becomes a whole one. Kept here so every rounding in the
 * domain is the same rounding — an aid computed one way on the phone and another way on the server
 * is exactly the divergence the golden vectors exist to catch.
 *
 * Half-away-from-zero is the intuitive reading of "arrondi au centime" and is what a reviewer
 * checking a devis by hand will do. `TODO(unverified)`: whether MaPrimeRénov' and the CEE barèmes
 * mandate a different rule is an M3 question with a citation attached, not an assumption to bake in
 * here. If they do, this function changes and the golden vectors below it record both eras.
 */
internal fun divideRoundingHalfAwayFromZero(numerator: Long, divisor: Long): Long {
    require(divisor > 0) { "divisor must be positive, was $divisor" }

    val quotient = numerator / divisor
    val remainder = numerator % divisor
    if (remainder == 0L) return quotient

    val roundsAway = remainder.absoluteMagnitude() * 2 >= divisor
    return when {
        !roundsAway -> quotient
        numerator < 0 -> quotient - 1
        else -> quotient + 1
    }
}

/** `abs` without the [Long.MIN_VALUE] trap; remainders are always well within range. */
private fun Long.absoluteMagnitude(): Long = if (this < 0) -this else this
