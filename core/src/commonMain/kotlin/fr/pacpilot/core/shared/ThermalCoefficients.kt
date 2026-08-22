package fr.pacpilot.core.shared

/**
 * The physical quantities a heat-loss method consumes as coefficients.
 *
 * Each is its own type for the same reason [PowerKw] and [TemperatureC] are: in a thermal
 * calculation every coefficient is "a number", and nothing but the type system stops an air-change
 * rate being multiplied where a U-value belongs. The result of that mistake is not a crash — it is
 * a plausible wrong kilowatt figure on a devis, which is the failure this product exists to prevent.
 *
 * All hold fixed-point integers, like every other unit here, and expose [magnitude] so the engine
 * can do its arithmetic without a single unit-scale literal of its own.
 *
 * **None of these carries a value.** They are containers; the numbers arrive from a validated
 * formula set at the M2 ⚑ gate.
 */

/** Thermal transmittance — a U-value in W/(m²·K), held as thousandths. */
data class ThermalTransmittance(val milliWattPerSquareMetreKelvin: Int) {

    init {
        require(milliWattPerSquareMetreKelvin >= 0) {
            "a U-value is not negative, was $milliWattPerSquareMetreKelvin mW/(m2.K)"
        }
    }

    val magnitude: Double get() = milliWattPerSquareMetreKelvin / SCALE

    fun render(): String = renderScaled(milliWattPerSquareMetreKelvin.toLong(), DECIMALS)

    override fun toString(): String = render() + " W/(m2.K)"

    private companion object {
        const val DECIMALS = 3
        const val SCALE = 1000.0
    }
}

/** Air renewal, in volumes per hour, held as thousandths. */
data class AirChangeRate(val milliVolumesPerHour: Int) {

    init {
        require(milliVolumesPerHour >= 0) {
            "an air-change rate is not negative, was $milliVolumesPerHour mvol/h"
        }
    }

    val magnitude: Double get() = milliVolumesPerHour / SCALE

    fun render(): String = renderScaled(milliVolumesPerHour.toLong(), DECIMALS)

    override fun toString(): String = render() + " vol/h"

    private companion object {
        const val DECIMALS = 3
        const val SCALE = 1000.0
    }
}

/**
 * The volumetric heat capacity of air, in Wh/(m³·K), held as thousandths.
 *
 * A physical constant rather than a method choice — but it is still a number, and this project's
 * standing rule is that no number appears in the engine without a source (`CLAUDE.md` §12). It
 * arrives through the formula set like every other coefficient, so the ⚑ gate can cite it.
 */
data class VolumetricHeatCapacity(val milliWattHourPerCubicMetreKelvin: Int) {

    init {
        require(milliWattHourPerCubicMetreKelvin > 0) {
            "air has a positive heat capacity, was $milliWattHourPerCubicMetreKelvin mWh/(m3.K)"
        }
    }

    val magnitude: Double get() = milliWattHourPerCubicMetreKelvin / SCALE

    fun render(): String = renderScaled(milliWattHourPerCubicMetreKelvin.toLong(), DECIMALS)

    override fun toString(): String = render() + " Wh/(m3.K)"

    private companion object {
        const val DECIMALS = 3
        const val SCALE = 1000.0
    }
}

/**
 * How much heat-losing envelope a dwelling has, per square metre of heated floor area.
 *
 * The simplification that lets a 15-minute pre-visit skip a full surface survey: rather than
 * measuring every wall, roof and floor, the method infers total envelope area from the floor area
 * the installer can read off a plan or pace out.
 *
 * **This factor is the simplification, and it is exactly what the ⚑ gate has to defend.** Its value
 * decides how far the method can be trusted, and it is the first thing an auditor would question.
 */
data class EnvelopeAreaFactor(val milliUnits: Int) {

    init {
        require(milliUnits > 0) { "an envelope-area factor is positive, was $milliUnits" }
    }

    val magnitude: Double get() = milliUnits / SCALE

    fun render(): String = renderScaled(milliUnits.toLong(), DECIMALS)

    override fun toString(): String = render() + " m2/m2"

    private companion object {
        const val DECIMALS = 3
        const val SCALE = 1000.0
    }
}
