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

    // M4 brings the persistence adapters the JDBC-only arrangement was holding the door open for.
    // The boundary tests landed first, in M4-02: BoundedContextRulesTest fails the build if an
    // @Entity appears outside a persistence adapter, if the application layer imports one, or if a
    // JPA annotation reaches :core. Hibernate arrives with the walls already standing, which is the
    // order M0-05's comment was asking for.
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertj.core)

    // The modular monolith's internal walls are a test, not a convention (M4-02). :core already has
    // its own ArchUnit suite proving the domain stays framework-free; this one proves the server's
    // contexts stay separable, which nothing guarded before M4.
    testImplementation(libs.archunit.junit5)

    // The migration is verified against a real PostgreSQL, because that is the only way to prove a
    // migration actually applies. The test disables itself when Docker is unavailable, so
    // `./gradlew build` stays runnable on a machine without it.
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()

    // The integration tests carry `disabledWithoutDocker`, so on a machine with no Docker daemon
    // they skip and the build still reports success. That is deliberate — but twice during M4 a
    // green summary meant "nothing ran", and both times a real defect was hiding behind it: the
    // @SpringBootConfiguration lookup failure in DossierPersistenceTest sat undetected for a whole
    // ticket because the suite never executed.
    //
    // So: always say out loud how many were skipped, and in CI make it fatal. `-PrequireDocker=true`
    // is what the workflow passes; locally the warning is enough, because refusing to build without
    // Docker is exactly the friction `disabledWithoutDocker` exists to avoid.
    val requireDocker = providers.gradleProperty("requireDocker").orNull.toBoolean()
    var skipped = 0L

    afterSuite(
        KotlinClosure2<TestDescriptor, TestResult, Unit>({ descriptor, result ->
            if (descriptor.parent == null) {
                skipped = result.skippedTestCount
            }
        }),
    )

    doLast {
        if (skipped > 0L) {
            val message =
                "$skipped test(s) were SKIPPED — most likely the Testcontainers suites, because no " +
                    "Docker daemon was reachable. A green build here does not mean the integration " +
                    "tests passed; it means they never ran."
            if (requireDocker) {
                throw GradleException("$message Failing because -PrequireDocker=true.")
            }
            logger.warn("\n⚠  $message\n   Start Docker and re-run, or pass -PrequireDocker=true to make this fatal.\n")
        }
    }
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
