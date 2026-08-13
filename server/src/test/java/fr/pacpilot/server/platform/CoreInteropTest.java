package fr.pacpilot.server.platform;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.CoreInfo;
import org.junit.jupiter.api.Test;

/**
 * Pins the Java ↔ Kotlin boundary introduced by ADR-0010.
 *
 * <p>This is the seam the whole arrangement depends on: Kotlin is confined to {@code :core} because
 * Multiplatform compiles one source set to both the JVM (consumed here) and JS (consumed by the
 * PWA), while everything on this side is Java. If that boundary stops working — or starts requiring
 * ceremony to cross — the argument for keeping Kotlin at all weakens, and we should find out from a
 * failing test rather than from friction discovered halfway through M4.
 *
 * <p>Deliberately a plain unit test with no Spring context: it isolates the language boundary from
 * every other moving part, so a failure here means exactly one thing.
 *
 * <p>M1 replaces this with assertions over the real domain types, where the interop constraints
 * recorded on {@code CoreInfo} actually bite — no {@code value class} in public signatures, no
 * default arguments, classes and interfaces over {@code object} singletons.
 */
class CoreInteropTest {

    @Test
    void javaCanCallIntoTheKotlinCore() {
        assertThat(CoreInfo.INSTANCE.identify()).isEqualTo("pac-pilot-core");
    }

    @Test
    void theCoreIsLoadedFromTheKotlinJvmTarget() {
        // Guards against the core silently being resolved from somewhere unexpected — a shaded jar,
        // a stale artifact, or the KMP metadata variant rather than the JVM one.
        var location = CoreInfo.INSTANCE.getClass().getProtectionDomain().getCodeSource().getLocation().toString();

        assertThat(location)
                .describedAs("core must resolve to the KMP JVM target artifact, was: %s", location)
                .containsAnyOf("core-jvm", "core/build/");
    }
}
