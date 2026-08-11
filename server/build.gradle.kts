// Modular monolith (CLAUDE.md §4.7): one deployable, hard internal walls between
// bounded contexts. Depends on the core's JVM target, which M4 wires as the verifier.
//
// The database is wired here from M0-05: a DataSource and Flyway, nothing more. No JPA and no
// Spring Data — persistence adapters and the aggregate schema belong to M4.

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

    // JDBC only — a DataSource for Flyway to migrate. Deliberately not spring-boot-starter-data-jpa:
    // JPA entities and repository adapters are M4's, and pulling Hibernate in now would let
    // persistence concerns reach the domain before the boundary tests of M4 exist to stop them.
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertj.core)

    // The migration is verified against a real PostgreSQL, because that is the only way to prove a
    // migration actually applies. The test disables itself when Docker is unavailable, so
    // `./gradlew build` stays runnable on a machine without it.
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
