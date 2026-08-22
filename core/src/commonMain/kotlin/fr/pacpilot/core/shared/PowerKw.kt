package fr.pacpilot.core.shared

/**
 * A thermal or electrical power, held exactly as a whole number of watts and read as kilowatts.
 *
 * The watt is the smallest unit any of this product's inputs distinguish — a catalogue entry quotes
 * power at −7 °C to a tenth of a kilowatt, and a heat load is not meaningful past the watt. Holding
 * the integer keeps the dimensioning result byte-identical on the installer's phone and on the
 * server, which is the whole point of the portable core.
 *
 * Non-negative: a heat load or an emitted power below zero is a modelling error, not a value.
 *
 * **Java surface (ADR-0010).** `new PowerKw(8_500)` from `:server`.
 */
data class PowerKw(val watts: Int) : Comparable<PowerKw> {

    init {
        require(watts >= 0) { "power is not negative, was $watts W" }
    }

    /** Canonical decimal string in kilowatts, always three places: `8.500`, `0.750`, `12.400`. */
    fun render(): String = renderScaled(watts.toLong(), DECIMALS)

    operator fun plus(other: PowerKw): PowerKw = PowerKw(watts + other.watts)

    override fun compareTo(other: PowerKw): Int = watts.compareTo(other.watts)

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "kW"
        private const val DECIMALS: Int = 3
        private const val WATTS_PER_KILOWATT: Int = 1_000

        val ZERO: PowerKw = PowerKw(0)

        fun ofKilowatts(kilowatts: Int): PowerKw = PowerKw(kilowatts * WATTS_PER_KILOWATT)
    }
}

/**
 * The power range a machine should fall in, rather than a single figure.
 *
 * Sizing to one exact kilowatt is false precision from a simplified method, and it is also the
 * wrong shape for the next step: PAC selection filters a catalogue by power at −7 °C, which is a
 * range query. Under-sizing leaves a cold client and over-sizing causes short-cycling — the two
 * failure modes named in `PRODUCT-VIEWS.md` — so both ends carry meaning.
 */
data class PowerBand(val minimum: PowerKw, val maximum: PowerKw) {

    init {
        require(minimum <= maximum) {
            "a power band runs upward, was " + minimum.render() + " to " + maximum.render()
        }
    }

    fun contains(power: PowerKw): Boolean = power >= minimum && power <= maximum
}
