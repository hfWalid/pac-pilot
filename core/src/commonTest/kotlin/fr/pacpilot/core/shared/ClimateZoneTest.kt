package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ClimateZoneTest {

    @Test
    fun `carries no coefficient of its own`() {
        // Pins the decision rather than the data: if a base temperature is ever attached to this
        // enum without a citation, this test is where the reviewer is meant to argue about it.
        // Coefficients belong to M2's injected FormulaSet, each with a source or SOURCE_TBD.
        assertEquals(3, ClimateZone.entries.size)
        assertEquals(listOf("H1", "H2", "H3"), ClimateZone.entries.map { it.name })
    }
}
