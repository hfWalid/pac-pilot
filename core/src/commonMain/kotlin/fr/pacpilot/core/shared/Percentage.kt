package fr.pacpilot.core.shared

/**
 * A rate, held exactly in basis points — one hundredth of one percent.
 *
 * Basis points because every rate this product touches is expressed to at most two decimals of a
 * percent and must land on the cent identically on both targets: TVA at 5,5 % is `550`, a
 * MaPrimeRénov' rate of 30 % is `3000`. Storing 0.055 as a `Double` and multiplying is how a
 * client-side reste-à-charge ends up one cent from the server's.
 *
 * Rates are non-negative. A reduction is modelled by the aggregate that applies it, not by a
 * negative rate — `Percentage(-500)` reads as "minus five percent of what?" and the answer always
 * lives somewhere else.
 *
 * **Java surface (ADR-0010).** `new Percentage(550)` from `:server`.
 */
data class Percentage(val basisPoints: Int) {

    init {
        require(basisPoints >= 0) {
            "a rate is not negative, was $basisPoints basis points; model a reduction where it is applied"
        }
    }

    /** Canonical decimal string in percent, always two places: `5.50`, `30.00`, `0.01`. */
    fun render(): String = renderScaled(basisPoints.toLong(), DECIMALS)

    /**
     * This rate of [amount], rounded to the cent by [divideRoundingHalfAwayFromZero].
     *
     * The rounding lives in one function on purpose. Two call sites rounding two ways is the
     * divergence the golden vectors exist to catch, and the boundary case is pinned by
     * `shared-units.vectors` rather than left to a reviewer's assumption.
     */
    fun applyTo(amount: MoneyEur): MoneyEur =
        MoneyEur(divideRoundingHalfAwayFromZero(amount.cents * basisPoints.toLong(), BASIS_POINTS_PER_UNIT))

    /**
     * This rate of [power], rounded to the watt by the same rule money uses.
     *
     * Shares [divideRoundingHalfAwayFromZero] with [applyTo] deliberately: a band margin rounded one
     * way and an aid rounded another is two rounding rules in one product, and the golden vectors
     * would pin the discrepancy rather than catch it.
     */
    fun applyTo(power: PowerKw): PowerKw =
        PowerKw(
            divideRoundingHalfAwayFromZero(
                power.watts.toLong() * basisPoints.toLong(),
                BASIS_POINTS_PER_UNIT,
            ).toInt(),
        )

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "%"
        private const val DECIMALS: Int = 2
        private const val BASIS_POINTS_PER_UNIT: Long = 10_000
        private const val BASIS_POINTS_PER_PERCENT: Int = 100

        val ZERO: Percentage = Percentage(0)

        fun ofWholePercent(percent: Int): Percentage = Percentage(percent * BASIS_POINTS_PER_PERCENT)
    }
}
