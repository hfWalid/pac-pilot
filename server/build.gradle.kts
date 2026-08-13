// Modular monolith (CLAUDE.md §4.7): one deployable, hard internal walls between
// bounded contexts. Depends on the core's JVM target, which M4 wires as the verifier.
//
// **Java, not Kotlin** (ADR-0010). Kotlin is confined to :core, where Multiplatform is the reason
// it exists. Everything on this side of the boundary — adapters, persistence, REST, PDF, sync —
// is Java 21 + Spring Boot.
//
// The database is wired here from M0-05: a DataSource and Flyway, nothing more. No JPA and no
// Spring Data — persistence adapters and the aggregate schema belong to M4.

plugins {
    java
    alias(libs.plugins.spring.boot)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
    }
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

// Guardrail against silent regression of ADR-0010. Without it, a single .kt file added here would
// compile only if someone also re-applied the Kotlin plugin — but the package layout would already
// have drifted, and the boundary is much easier to defend while it is still empty.
val verifyNoKotlinInServer by tasks.registering {
    description = "Fails if any Kotlin source appears in :server — Kotlin belongs to :core only."
    group = "verification"

    val sourceRoot = layout.projectDirectory.dir("src")
    inputs.dir(sourceRoot)
    // Declared so the task is not perpetually out of date; it has no real artifact.
    outputs.file(layout.buildDirectory.file("verifyNoKotlinInServer.ok"))

    doLast {
        val offenders = sourceRoot.asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(projectDir).path }
            .toList()

        if (offenders.isNotEmpty()) {
            throw GradleException(
                """
                Kotlin source found in :server — see ADR-0010.

                ${offenders.joinToString("\n                ")}

                Kotlin is confined to :core, where Kotlin Multiplatform compiles one source set to
                both the JVM (this module's verifier) and JS (the PWA). :server is Java.
                """.trimIndent(),
            )
        }
        layout.buildDirectory.file("verifyNoKotlinInServer.ok").get().asFile.apply {
            parentFile.mkdirs()
            writeText("ok")
        }
    }
}

tasks.check {
    dependsOn(verifyNoKotlinInServer)
}
