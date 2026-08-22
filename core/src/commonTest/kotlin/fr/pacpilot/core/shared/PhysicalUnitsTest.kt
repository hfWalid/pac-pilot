package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhysicalUnitsTest {

    @Test
    fun `power renders three decimal places of a kilowatt`() {
        assertEquals("8.500", PowerKw(8_500).render())
        assertEquals("1.000", PowerKw.ofKilowatts(1).render())
        assertEquals("0.000", PowerKw.ZERO.render())
    }

    @Test
    fun `power adds exactly`() {
        assertEquals(PowerKw(9_250), PowerKw(8_500) + PowerKw(750))
    }

    @Test
    fun `power refuses a negative value`() {
        assertFailsWith<IllegalArgumentException> { PowerKw(-1) }
    }

    @Test
    fun `temperature renders one decimal place and keeps the sign`() {
        // The outdoor design temperature is the negative one, and it drives the heat load.
        assertEquals("-7.0", TemperatureC(-70).render())
        assertEquals("20.0", TemperatureC.ofWholeDegrees(20).render())
        assertEquals("-0.5", TemperatureC(-5).render())
        assertEquals("0.0", TemperatureC.ZERO.render())
    }

    @Test
    fun `surface renders two decimal places`() {
        assertEquals("120.55", SurfaceM2(12_055).render())
        assertEquals("85.00", SurfaceM2.ofWholeSquareMetres(85).render())
    }

    @Test
    fun `surface refuses zero and below`() {
        assertFailsWith<IllegalArgumentException> { SurfaceM2(0) }
        assertFailsWith<IllegalArgumentException> { SurfaceM2(-1) }
    }
}

class TemperatureDifferenceTest {

    @Test
    fun `subtracting two temperatures yields the gap the heat-load formula multiplies`() {
        // 19,0 C indoors against a base temperature of -7,0 C is a 26,0 K gap.
        val gap = TemperatureC.ofWholeDegrees(19) - TemperatureC(-70)
        assertEquals("26.0", gap.render())
        assertEquals(TemperatureDifferenceC(260), gap)
    }

    @Test
    fun `the gap keeps its sign when the subtraction runs the other way`() {
        assertEquals("-26.0", (TemperatureC(-70) - TemperatureC.ofWholeDegrees(19)).render())
    }

    @Test
    fun `a difference is not a temperature`() {
        // The whole point of the separate type: a magnitude is not a position on the scale, so a
        // 26 K gap cannot be passed where an indoor target is expected. That is a compile-time
        // guarantee; what is assertable here is that the two are distinct values.
        assertEquals("26.0 K", (TemperatureC.ofWholeDegrees(19) - TemperatureC(-70)).toString())
        assertEquals("26.0 C", TemperatureC.ofWholeDegrees(26).toString())
    }

    @Test
    fun `a subscribed electrical supply is whole and positive`() {
        assertEquals("9", ElectricalSupplyKva(9).render())
        assertEquals("9 kVA", ElectricalSupplyKva(9).toString())
        assertFailsWith<IllegalArgumentException> { ElectricalSupplyKva(0) }
    }
}
