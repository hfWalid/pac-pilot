package fr.pacpilot.core

/**
 * Placeholder proving one `commonMain` source compiles and runs on both the JVM and JS targets.
 *
 * It exists only to make the two-target mechanism verifiable before any domain code depends on it.
 * The real domain model arrives in M1; delete this once [fr.pacpilot.core.shared] carries real types.
 */
object CoreInfo {

    /** Identifies the core across both targets. Asserted by the M0-07 golden-vector harness. */
    fun identify(): String = "pac-pilot-core"
}
