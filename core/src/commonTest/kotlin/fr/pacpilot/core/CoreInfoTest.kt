package fr.pacpilot.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs on both targets (`:core:jvmTest` and `:core:jsTest`). If either is skipped, the two-target
 * guarantee is not actually being checked — M0-08 asserts both appear in the CI log.
 */
class CoreInfoTest {

    @Test
    fun `identifies the core identically on every target`() {
        assertEquals("pac-pilot-core", CoreInfo.identify())
    }
}
