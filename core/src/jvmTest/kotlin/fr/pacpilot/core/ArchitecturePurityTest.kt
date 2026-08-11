package fr.pacpilot.core

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Enforces that the core stays framework-free (CLAUDE.md §4.1, §10, §12).
 *
 * This is not merely an architectural preference here. The core must compile to **JS**, so a Spring
 * or JPA import does not just offend the hexagon — it breaks the JS target outright, and it would
 * also make the engines un-embeddable as the SDK described in CLAUDE.md §3. Failing here gives a
 * readable message instead of a compiler error in a target most people are not looking at.
 *
 * **Why the JVM test source set:** ArchUnit reads JVM bytecode, so it cannot inspect `commonMain`
 * directly. It does not need to — `commonMain` compiles *into* the JVM target's output, so
 * importing that output covers exactly the code these rules are meant to protect, plus the thin
 * `jvmMain` glue. Anything reaching `commonMain` therefore fails here too.
 *
 * The rule list is deliberately short and specific. A vague "no frameworks" rule nobody can
 * interpret gets suppressed the first time it fires.
 */
class ArchitecturePurityTest {

    private val coreClasses: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("fr.pacpilot.core")

    @Test
    fun `the core does not depend on Spring`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .because(
                "the core is framework-free and compiles to JS; Spring in commonMain breaks the " +
                    "JS target and couples the domain to the server",
            )
            .check(coreClasses)
    }

    @Test
    fun `the core does not depend on JPA or JDBC`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "javax.persistence..",
                "java.sql..",
                "javax.sql..",
                "org.hibernate..",
            )
            .because(
                "persistence is an adapter concern (ARCHITECTURE #5); JPA entities live in " +
                    ":server and are mapped to the domain, never merged with it",
            )
            .check(coreClasses)
    }

    @Test
    fun `the core does not depend on a serialization framework`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.fasterxml.jackson..",
                "kotlinx.serialization..",
            )
            .because(
                "wire formats belong to adapters; a serialization annotation in the domain lets " +
                    "transport concerns dictate the model's shape",
            )
            .check(coreClasses)
    }

    @Test
    fun `the core does not depend on servlet or web infrastructure`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.servlet..",
                "javax.servlet..",
                "org.apache.tomcat..",
            )
            .check(coreClasses)
    }

    @Test
    fun `the core does not know the server exists`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("fr.pacpilot.server..")
            .because(
                "dependencies point inward: :server depends on :core, never the reverse " +
                    "(ARCHITECTURE #5)",
            )
            .check(coreClasses)
    }

    @Test
    fun `the core does not read a clock`() {
        // Determinism (CLAUDE.md §10): same inputs + same rule version = identical output, forever.
        // A hidden clock would not fail any test today — it would quietly make past devis
        // irreproducible years from now, which is when it matters. The effective date is an input.
        //
        // kotlin.time.Clock is called out by name because it is the one a Kotlin Multiplatform
        // author reaches for first: it is available in commonMain with no dependency, unlike
        // java.time (JVM-only) and kotlinx-datetime (not on the classpath). The package itself is
        // not banned — kotlin.time.Duration is legitimate and useful.
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                "java.time..",
                "kotlinx.datetime..",
            )
            .orShould().dependOnClassesThat()
            .haveNameMatching("""kotlin\.time\.(Clock|TimeSource).*""")
            .because(
                "engines take the effective date as a parameter; M1-03 introduces an EffectiveDate " +
                    "value object, and this rule is expected to be narrowed then, not deleted",
            )
            .check(coreClasses)
    }
}
