package fr.pacpilot.server.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The modular monolith's internal walls (ARCHITECTURE #4, {@code CLAUDE.md} §4.7), built while the
 * contexts are still mostly empty — the only time it is cheap.
 *
 * <p>The epic names the risk exactly: leakage. JPA annotations creeping into the domain, or one
 * context reaching into another's tables. Both are trivial to prevent by test and expensive to
 * unwind once a dozen classes depend on the shortcut. {@code CLAUDE.md} §4.7 requires that any
 * context be extractable later without a rewrite; that is only true if nothing ever reached inside
 * one.
 *
 * <p>Each rule is short, specific, and carries a {@code because} a reader can act on. A vague
 * "no leakage" rule gets suppressed the first time it fires.
 *
 * <p><b>Every rule here was proven to fail before being trusted</b>, by writing the violation it
 * exists to catch and watching the build go red — six deliberate violations, six named failures,
 * all deleted afterwards. The seventh rule, {@code theDomainCarriesNoPersistenceAnnotations},
 * could not be violated without breaking {@code :core}'s JS target outright; it is already proven
 * by {@code :core}'s own purity suite, verified at M0-06.
 *
 * <p>One trap worth recording, because it made two rules look green while catching nothing. The
 * first attempt at a cross-context violation reached for a {@code public static final String} in
 * another context. The compiler inlines constants, so the compiled class carried <em>no dependency
 * at all</em> and both the reach-through rule and the inward-dependency rule passed. ArchUnit reads
 * bytecode, not source. A violation used to prove a rule must be a real reference — a type, a
 * method call, a field access — or it proves nothing.
 *
 * <p><b>The package convention these rules enforce.</b> Within {@code fr.pacpilot.server.<context>}:
 *
 * <ul>
 *   <li>{@code api} — the deliberate surface. The only thing another context may see.
 *   <li>{@code application} — use cases. Knows the domain and the ports; knows no framework.
 *   <li>{@code adapter.in.web} — REST. {@code adapter.out.persistence} — JPA entities and repositories.
 * </ul>
 */
class BoundedContextRulesTest {

    private static final String ROOT = "fr.pacpilot.server";

    /**
     * The bounded contexts of ARCHITECTURE #4, plus the two pulled forward at M4-01 —
     * {@code interventions} (ADR-0012) and {@code identity} (ADR-0013).
     *
     * <p>Listed explicitly rather than discovered from the package tree: a context added without
     * being named here would silently escape every rule below, and the failure mode of that is a
     * wall nobody knows is missing.
     */
    private static final Set<String> CONTEXTS =
            Set.of(
                    "dossier",
                    "catalog",
                    "dimensioning",
                    "aids",
                    "quoting",
                    "interventions",
                    "identity",
                    "sync",
                    "platform");

    /**
     * {@code platform} is the composition root: it holds the Spring application, configuration and
     * cross-cutting concerns, and wiring a monolith together is precisely its job. It is the one
     * context permitted to see another's internals.
     *
     * <p>Recorded as a decision rather than an oversight. If {@code platform} ever grows business
     * logic, this exemption becomes a laundering route and the rule should be tightened.
     */
    private static final String COMPOSITION_ROOT = "platform";

    private final JavaClasses serverClasses =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    // ── Context isolation ────────────────────────────────────────────────────────────────────

    @Test
    void crossContextTrafficGoesThroughTheExposedSurfaceOnly() {
        classes()
                .should(onlyReachOtherContextsThroughTheirApi())
                .because(
                        "a context that can be reached inside is not extractable later; cross-context "
                                + "traffic goes through <context>.api, which is the surface its owner "
                                + "chose to defend")
                .check(serverClasses);
    }

    @Test
    void everyContextIsOneOfTheContextsThisSuiteKnowsAbout() {
        // Guards the list above. A new context package that nobody added to CONTEXTS would be
        // invisible to every rule in this class — a missing wall is worse than a wall that fires.
        List<String> unknown =
                serverClasses.stream()
                        .map(JavaClass::getPackageName)
                        .filter(name -> name.startsWith(ROOT + "."))
                        .map(name -> name.substring(ROOT.length() + 1).split("\\.")[0])
                        .distinct()
                        .filter(context -> !CONTEXTS.contains(context))
                        .toList();

        assertThat(unknown)
                .as(
                        "package(s) under %s that are not declared bounded contexts — add them to "
                                + "CONTEXTS, or move the class into a context that already exists",
                        ROOT)
                .isEmpty();
    }

    // ── Dependency direction ─────────────────────────────────────────────────────────────────


    @Test
    void adaptersDependInwardAndTheApplicationLayerNeverDependsOnThem() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .because(
                        "dependencies point toward the application core (ARCHITECTURE #5); an "
                                + "application service that knows an adapter cannot be tested without "
                                + "one, and the adapter stops being replaceable")
                .check(serverClasses);
    }

    @Test
    void theApplicationLayerIsFreeOfTransportAndPersistence() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "org.hibernate..",
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "org.springframework.data..")
                .because(
                        "a use case is stated in domain terms; an HTTP status or an @Entity in the "
                                + "application layer is a transport or storage decision that has "
                                + "leaked into a business rule")
                .check(serverClasses);
    }

    // ── Persistence containment ──────────────────────────────────────────────────────────────

    @Test
    void jpaEntitiesLiveOnlyInPersistenceAdapters() {
        classes()
                .that()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .should()
                .resideInAPackage("..adapter.out.persistence..")
                .because(
                        "an entity outside a persistence adapter is one import away from being "
                                + "passed to a use case, and mapping stops being explicit")
                .check(serverClasses);
    }

    @Test
    void everyEntityMapsATableItsOwnContextOwns() {
        // Table-level isolation, which bytecode rules alone do not reach: a JPA entity in `quoting`
        // declaring @Table(name = "client") reads another context's table without importing a single
        // class from it. The convention is that a table is prefixed with its owning context.
        List<String> trespassers =
                serverClasses.stream()
                        .filter(type -> type.isAnnotatedWith("jakarta.persistence.Entity"))
                        .map(
                                type ->
                                        contextOf(type)
                                                .flatMap(context -> tableTrespassOf(type, context))
                                                .orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList();

        assertThat(trespassers)
                .as(
                        "entities mapping a table outside their own context — a table is owned by "
                                + "exactly one context and read through its api, never by mapping it "
                                + "twice (DELIVERY-PLAN §3)")
                .isEmpty();
    }

    @Test
    void theDomainCarriesNoPersistenceAnnotations() {
        // :core has its own rule for this, but it is asserted again from this side because this is
        // where the pressure comes from: the temptation is to annotate a domain class here, and the
        // failure should name the server module that reached for it.
        noClasses()
                .that()
                .resideInAPackage("fr.pacpilot.core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                .because(
                        "the domain model carries zero JPA annotations; mapping lives in adapters, "
                                + "and a shared class is how JPA reaches the domain (ADR-0010)")
                .check(
                        new ClassFileImporter()
                                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                                .importPackages("fr.pacpilot.core"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /** The bounded context a class belongs to, if it is under one. */
    private static Optional<String> contextOf(JavaClass type) {
        String packageName = type.getPackageName();
        if (!packageName.startsWith(ROOT + ".")) {
            return Optional.empty();
        }
        String context = packageName.substring(ROOT.length() + 1).split("\\.")[0];
        return CONTEXTS.contains(context) ? Optional.of(context) : Optional.empty();
    }

    /** A description of the trespass, or empty when the entity maps a table its context owns. */
    private static Optional<String> tableTrespassOf(JavaClass entity, String context) {
        String table =
                entity.tryGetAnnotationOfType(jakarta.persistence.Table.class)
                        .map(jakarta.persistence.Table::name)
                        .filter(name -> !name.isBlank())
                        .orElse("");

        if (table.isEmpty()) {
            return Optional.of(
                    entity.getName() + " declares no @Table(name = …); the owning context must be explicit");
        }
        if (table.equals(context) || table.startsWith(context + "_")) {
            return Optional.empty();
        }
        return Optional.of(entity.getName() + " in context '" + context + "' maps table '" + table + "'");
    }

    private static ArchCondition<JavaClass> onlyReachOtherContextsThroughTheirApi() {
        return new ArchCondition<>("only reach other contexts through their api package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Optional<String> originContext = contextOf(origin);
                if (originContext.isEmpty() || originContext.get().equals(COMPOSITION_ROOT)) {
                    return;
                }

                origin.getDirectDependenciesFromSelf()
                        .forEach(
                                dependency -> {
                                    JavaClass target = dependency.getTargetClass();
                                    Optional<String> targetContext = contextOf(target);
                                    if (targetContext.isEmpty() || targetContext.get().equals(originContext.get())) {
                                        return;
                                    }
                                    String apiPackage = ROOT + "." + targetContext.get() + ".api";
                                    String targetPackage = target.getPackageName();
                                    boolean throughApi =
                                            targetPackage.equals(apiPackage)
                                                    || targetPackage.startsWith(apiPackage + ".");
                                    if (!throughApi) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        origin,
                                                        origin.getName()
                                                                + " ("
                                                                + originContext.get()
                                                                + ") reaches into "
                                                                + target.getName()
                                                                + "; go through "
                                                                + apiPackage));
                                    }
                                });
            }
        };
    }
}
