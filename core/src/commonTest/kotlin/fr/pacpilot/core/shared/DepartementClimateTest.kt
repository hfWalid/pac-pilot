package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DepartementClimateTest {

    @Test
    fun `a departement code is not a number`() {
        // 2A and 2B exist. Parsing these as integers is the classic bug this type prevents.
        assertEquals("2A", Departement("2A").code)
        assertEquals("69", Departement("69").code)
        assertEquals("974", Departement("974").code)
    }

    @Test
    fun `refuses a blank, padded or lower-case code`() {
        assertFailsWith<IllegalArgumentException> { Departement("") }
        assertFailsWith<IllegalArgumentException> { Departement(" 69") }
        assertFailsWith<IllegalArgumentException> { Departement("2a") }
    }

    @Test
    fun `a base temperature declares its source or says plainly it has none`() {
        val provisional = DepartementClimate(
            departement = Departement("69"),
            zone = ClimateZone.H1,
            baseTemperature = TemperatureC(-70),
            source = DepartementClimate.SOURCE_TBD + " (awaiting the M2 method validation gate)",
        )
        assertTrue(provisional.isProvisional)

        val cited = provisional.copy(source = "RT2012 annexe, zone H1")
        assertTrue(!cited.isProvisional)

        assertFailsWith<IllegalArgumentException> { provisional.copy(source = "  ") }
    }
}
