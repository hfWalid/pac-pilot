package fr.pacpilot.core.shared

/**
 * A temperature in degrees Celsius, held exactly as tenths of a degree.
 *
 * A tenth is the precision the domain actually uses: base temperatures are tabulated per zone at
 * whole or half degrees, and a catalogue quotes power at −7 °C. Negative values are ordinary here —
 * the base temperature *is* the negative one, and it is the single most important input to the
 * heat-load calculation, which is why the negative rendering path is pinned by a golden vector.
 *
 * No absolute-zero floor is enforced. The type does not know which of its uses is an outdoor design
 * temperature and which is a flow temperature; the engine that consumes it does, and M2's
 * validation belongs there rather than as a constant invented here.
 *
 * **Java surface (ADR-0010).** `new TemperatureC(-70)` from `:server`.
 */
data class TemperatureC(val deciCelsius: Int) : Comparable<TemperatureC> {

    /** Canonical decimal string, always one place: `-7.0`, `20.0`, `55.5`. */
    fun render(): String = renderScaled(deciCelsius.toLong(), DECIMALS)

    override fun compareTo(other: TemperatureC): Int = deciCelsius.compareTo(other.deciCelsius)

    /**
     * The gap between two temperatures — the quantity a heat-loss calculation actually multiplies.
     *
     * Returns a [TemperatureDifferenceC], not a `TemperatureC`. Adding two temperatures is
     * physically meaningless and is deliberately absent; subtracting them is meaningful and yields
     * a different kind of thing, so the type says so rather than letting a delta masquerade as a
     * position on the scale.
     */
    operator fun minus(other: TemperatureC): TemperatureDifferenceC =
        TemperatureDifferenceC(deciCelsius - other.deciCelsius)

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "C"
        private const val DECIMALS: Int = 1
        private const val DECI_PER_DEGREE: Int = 10

        val ZERO: TemperatureC = TemperatureC(0)

        fun ofWholeDegrees(degrees: Int): TemperatureC = TemperatureC(degrees * DECI_PER_DEGREE)
    }
}

/**
 * A difference between two temperatures, in tenths of a degree.
 *
 * Distinct from [TemperatureC] because the two are not interchangeable: 19 °C is a position on the
 * scale, while the 26-degree gap between 19 °C and −7 °C is a magnitude. The heat-load formula
 * multiplies the magnitude; a type that let a position be passed instead would compile and give a
 * plausible wrong answer, which is the failure mode this project cannot afford.
 *
 * Signed: the sign says which way round the subtraction went, and the engine at M2 decides whether
 * a negative gap is an input error or simply a warm day.
 */
data class TemperatureDifferenceC(val deciKelvin: Int) : Comparable<TemperatureDifferenceC> {

    /** Canonical decimal string, always one place: `26.0`, `-1.5`. */
    fun render(): String = renderScaled(deciKelvin.toLong(), 1)

    override fun compareTo(other: TemperatureDifferenceC): Int =
        deciKelvin.compareTo(other.deciKelvin)

    override fun toString(): String = render() + " K"
}
