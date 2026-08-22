package fr.pacpilot.core.shared

/**
 * An under-ceiling height in metres, held exactly as centimetres.
 *
 * **A deviation from DELIVERY-PLAN §2**, which lists the shared units without a length. Recorded
 * rather than smuggled: `CLAUDE.md` §6a names ceiling height as a dimensioning input, and the
 * alternative — a bare `Int` of centimetres inside `InputsSnapshot` — is the primitive obsession
 * that puts a surface and a height one typo apart.
 *
 * Named for the one thing it measures instead of a general `LengthM`. There is no second length in
 * the V1 wedge, and a general unit invites a metre of pipe run into a heat-load calculation.
 *
 * **Java surface (ADR-0010).** `new CeilingHeightM(250)` from `:server`.
 */
data class CeilingHeightM(val centimetres: Int) : Comparable<CeilingHeightM> {

    init {
        require(centimetres > 0) { "a ceiling height is strictly positive, was $centimetres cm" }
    }

    /** Canonical decimal string in metres, always two places: `2.50`, `3.05`. */
    fun render(): String = renderScaled(centimetres.toLong(), DECIMALS)

    /** Metres as a real number, for engine arithmetic. */
    val magnitude: Double get() = centimetres / CENTIMETRES_PER_METRE.toDouble()

    override fun compareTo(other: CeilingHeightM): Int = centimetres.compareTo(other.centimetres)

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "m"
        private const val DECIMALS: Int = 2
        private const val CENTIMETRES_PER_METRE: Int = 100

        fun ofWholeMetres(metres: Int): CeilingHeightM = CeilingHeightM(metres * CENTIMETRES_PER_METRE)
    }
}
