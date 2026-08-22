package fr.pacpilot.core.shared

/**
 * A heated surface in square metres, held exactly as hundredths.
 *
 * The installer measures or reads a surface off a plan; a hundredth of a square metre is already
 * finer than the input is trustworthy, and it keeps the integer arithmetic exact through the
 * dimensioning chain.
 *
 * Strictly positive: a dossier with a zero-surface site is an input error the survey screen should
 * refuse, and letting it through here would produce a confident heat load of zero.
 *
 * **Java surface (ADR-0010).** `new SurfaceM2(12_055)` from `:server`.
 */
data class SurfaceM2(val centiSquareMetres: Int) : Comparable<SurfaceM2> {

    init {
        require(centiSquareMetres > 0) { "a heated surface is strictly positive, was $centiSquareMetres cm2" }
    }

    /** Canonical decimal string, always two places: `120.55`, `85.00`. */
    fun render(): String = renderScaled(centiSquareMetres.toLong(), DECIMALS)

    /** Square metres as a real number, for engine arithmetic. Keeps unit scales out of the engine. */
    val magnitude: Double get() = centiSquareMetres / CENTI_PER_SQUARE_METRE.toDouble()

    override fun compareTo(other: SurfaceM2): Int = centiSquareMetres.compareTo(other.centiSquareMetres)

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "m2"
        private const val DECIMALS: Int = 2
        private const val CENTI_PER_SQUARE_METRE: Int = 100

        fun ofWholeSquareMetres(squareMetres: Int): SurfaceM2 =
            SurfaceM2(squareMetres * CENTI_PER_SQUARE_METRE)
    }
}
