// Root build. Plugins are declared here (not applied) so every module resolves
// the same version from gradle/libs.versions.toml.
plugins {
    // `base` gives the root a real lifecycle `build` task. Without it, `./gradlew build`
    // silently abbreviation-matches `buildEnvironment` and reports success without building.
    base
    // Kotlin is confined to :core, where Multiplatform compiles one source set to JVM and JS
    // (ADR-0002, ADR-0010). :server is Java, so no kotlin-jvm or kotlin-spring plugin is declared.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "fr.pacpilot"
    version = "0.1.0-SNAPSHOT"
}

// One Node version for the whole build.
//
// Kotlin/JS provisions its own Node to run jsTest, while :web shells out to whatever `npm` is on
// PATH. Left alone those are different runtimes — the JS the golden vectors execute under would not
// be the JS the PWA is bundled with. Both are pinned to the value in .nvmrc: Gradle downloads that
// version for Kotlin/JS, and :web validates the ambient toolchain against it (see web/build.gradle.kts).
//
// Kotlin Multiplatform installs a NodeJsEnvSpec on EVERY project carrying a JS target, not only on
// the root. Configuring the root project's plugins container alone left :core — the project that
// actually compiles and tests Kotlin/JS — on the plugin default, so the pin is applied across all
// projects. (The legacy NodeJsRootExtension.version is deprecated for removal and superseded by
// this spec, so it is deliberately not set.)
//
// The pinned version tracks the Node **LTS** line, not Current. Correctness and reproducibility are
// the stated priorities here, and Current gets breaking changes and a short support window; LTS is
// also what `nvm use` resolves to by default, so the pin costs no day-to-day friction.
val pinnedNodeVersion: String = file(".nvmrc").readText().trim()

allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set(pinnedNodeVersion)
    }
}

extra["pinnedNodeVersion"] = pinnedNodeVersion
