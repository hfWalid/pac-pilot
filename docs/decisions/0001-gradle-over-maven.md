# ADR-0001 — Gradle over Maven

- **Status:** accepted
- **Date:** 2026-08-11

## Context

The brief originally left the build tool open ("Maven or Gradle — pick and stay consistent"). The
question stopped being open once the calculation core was settled as Kotlin Multiplatform
([ADR-0002](0002-kotlin-multiplatform-core.md)): KMP's tooling is a Gradle plugin, and there is no
supported Maven path for compiling one source set to a JVM and a JS target.

## Decision

Gradle with the Kotlin DSL, driven by the wrapper, with all dependency versions in a version catalog
at `gradle/libs.versions.toml`.

## Consequences

- KMP works as intended, including the JS target and its generated TypeScript declarations.
- One entry point for CI across four heterogeneous modules — `./gradlew build` also drives the npm
  build of `:web`, so CI does not need to know about Node tooling separately.
- Kotlin DSL build files are compiled, so build-script errors surface at configuration time rather
  than as a runtime surprise.
- Cost: Gradle's task model has sharp edges this project has already hit twice — abbreviation
  matching silently resolved `build` to `buildEnvironment` on a plugin-less root, and
  `jsProductionLibraryDistribution` appeared to work on the command line while not existing as a
  real task name. Build scripts must use exact task names, and lifecycle tasks must actually exist.
- Cost: KMP double compilation is slow; the build cache is not optional in CI.

## Alternatives considered

**Maven.** Rejected: no viable Kotlin Multiplatform support. Choosing it would have meant abandoning
the single-source-of-truth core, which is the keystone of the architecture.

**Gradle Groovy DSL.** Rejected: no type safety in build scripts, and the Kotlin DSL is the default
for a Kotlin project.
