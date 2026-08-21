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
