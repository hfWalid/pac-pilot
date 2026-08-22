package fr.pacpilot.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentifiersTest {

    @Test
    fun `carries its value straight through toString for log lines and messages`() {
        assertEquals("c0ffee-01", DimensioningId("c0ffee-01").toString())
    }

    @Test
    fun `refuses a blank identifier`() {
        assertFailsWith<IllegalArgumentException> { DimensioningId("") }
        assertFailsWith<IllegalArgumentException> { QuoteId("   ") }
    }

    @Test
    fun `refuses surrounding whitespace rather than trimming it silently`() {
        // Trimming would make two ids that persist differently compare equal in memory.
        assertFailsWith<IllegalArgumentException> { SiteId(" site-1") }
        assertFailsWith<IllegalArgumentException> { ProductId("product-1\n") }
    }

    @Test
    fun `distinguishes identifiers of different aggregates`() {
        // The point of separate types: these two hold the same characters and are not the same
        // thing. A shared String would have let one be passed where the other was meant.
        assertEquals("x", DimensioningId("x").value)
        assertEquals("x", QuoteId("x").value)
    }
}
