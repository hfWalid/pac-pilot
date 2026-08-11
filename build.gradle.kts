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
