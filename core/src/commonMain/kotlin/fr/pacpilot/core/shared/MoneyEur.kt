package fr.pacpilot.core.shared

/**
 * An amount in euros, held exactly as a whole number of cents.
 *
 * Money is never a `Double` here. Aids, TVA and the reste-à-charge are shown to a homeowner on-site
 * and then recomputed server-side; a binary fraction that cannot represent 0.10 exactly turns that
 * into a divergence flag over a rounding artefact. Cents are exact, and exact is reproducible three
 * years later when a devis is audited.
 *
 * Negative amounts are permitted: a discount or a correction line is a legitimate amount, and
 * forbidding the sign here would push the concept into a parallel type. Whether a *particular*
 * amount may be negative is the owning aggregate's rule, not this type's.
 *
 * **Java surface (ADR-0010).** `new MoneyEur(150_00L)` from `:server`. Plain data class, no
 * `value class`, per the interop constraints recorded on `CoreInfo`.
 */
data class MoneyEur(val cents: Long) : Comparable<MoneyEur> {

    /** Canonical decimal string, always two places: `1234.56`, `-8.00`, `0.07`. */
    fun render(): String = renderScaled(cents, DECIMALS)

    operator fun plus(other: MoneyEur): MoneyEur = MoneyEur(cents + other.cents)

    operator fun minus(other: MoneyEur): MoneyEur = MoneyEur(cents - other.cents)

    /** Scales by a whole count — a line item's quantity, never a rate. Rates go through [Percentage]. */
    operator fun times(quantity: Int): MoneyEur = MoneyEur(cents * quantity)

    override fun compareTo(other: MoneyEur): Int = cents.compareTo(other.cents)

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "EUR"
        private const val DECIMALS: Int = 2
        private const val CENTS_PER_EURO: Long = 100

        val ZERO: MoneyEur = MoneyEur(0)

        fun ofEuros(euros: Long): MoneyEur = MoneyEur(euros * CENTS_PER_EURO)
    }
}
