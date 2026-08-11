// Root build. Plugins are declared here (not applied) so every module resolves
// the same version from gradle/libs.versions.toml.
plugins {
    // `base` gives the root a real lifecycle `build` task. Without it, `./gradlew build`
    // silently abbreviation-matches `buildEnvironment` and reports success without building.
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
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
val pinnedNodeVersion: String = file(".nvmrc").readText().trim()

plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set(pinnedNodeVersion)
}

extra["pinnedNodeVersion"] = pinnedNodeVersion
