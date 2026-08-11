// Modular monolith (CLAUDE.md §4.7): one deployable, hard internal walls between
// bounded contexts. Depends on the core's JVM target, which M4 wires as the verifier.
//
// No Spring Data / JPA here yet — the database arrives at M0-05, persistence at M4.

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    // The Spring Boot Gradle plugin does not apply BOM versions on its own; importing the
    // platform is what lets the spring-boot-* aliases stay version-less in the catalog.
    implementation(platform(libs.spring.boot.dependencies))

    // The JVM target specifically, not the KMP metadata artifact.
    implementation(project(":core"))

    implementation(libs.spring.boot.starter.web)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
