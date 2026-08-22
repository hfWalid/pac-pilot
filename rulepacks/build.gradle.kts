// The barème pipeline: encoded sources in, validated and signed packs out (CLAUDE.md §4.4, §7).
//
// **Java, not Kotlin** (ADR-0010). Kotlin is confined to :core; everything else is Java 21. This
// module reads the core's model types, which M4 already proved is comfortable from Java.
//
// **Not a Spring module and not deployed.** It is operator tooling, run from the command line four
// times a year (PAC-74's runbook). Publishing is a deliberate act, never a build step and never
// something CI can trigger on a merge.

plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
}

dependencies {
    // The JVM target specifically, for AidRulePack and the three AidRule mechanisms.
    implementation(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "fr.pacpilot.rulepacks.PackPipeline"
}

tasks.test {
    useJUnitPlatform()
}
