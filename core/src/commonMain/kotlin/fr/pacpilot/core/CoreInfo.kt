package fr.pacpilot.core

/**
 * Placeholder proving one `commonMain` source compiles and runs on both the JVM and JS targets.
 *
 * It exists only to make the two-target mechanism verifiable before any domain code depends on it.
 * The real domain model arrives in M1; delete this once [fr.pacpilot.core.shared] carries real types.
 *
 * **Java interop (ADR-0010).** This module's JVM consumer is `:server`, written in Java, so the
 * public surface must stay comfortably callable from Java. `@JvmStatic` and `@JvmName` cannot help
 * here — they are JVM-only annotations and do not resolve in `commonMain` — so the constraint is met
 * by API *design* instead, and proved by `CoreInteropTest` in `:server`:
 *
 *  - Prefer classes and interfaces over `object` singletons on the public surface. Engines are
 *    classes behind ports and get injected, so this costs nothing. A Kotlin `object` is reachable
 *    from Java only as `Xxx.INSTANCE.member()`, which is why this placeholder reads awkwardly there.
 *  - Avoid `value class` in public signatures: the JVM name is mangled and effectively un-callable
 *    from Java. This directly constrains M1-01's unit types — use plain data classes.
 *  - Avoid default arguments on public functions; Java sees only the full-arity overload.
 *  - Sealed hierarchies are fine, and read well under Java 21 pattern matching.
 */
object CoreInfo {

    /** Identifies the core across both targets. Asserted by the M0-07 golden-vector harness. */
    fun identify(): String = "pac-pilot-core"
}
