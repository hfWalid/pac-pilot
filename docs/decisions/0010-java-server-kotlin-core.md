# ADR-0010 — Java for the server; Kotlin confined to the core

- **Status:** accepted
- **Date:** 2026-08-11
- **Relates to:** [ADR-0002](0002-kotlin-multiplatform-core.md) (unchanged), [ADR-0001](0001-gradle-over-maven.md) (rationale still holds)

## Context

The team's strongest and most productive context is Java/Spring. Writing the server in Kotlin adds a
language to learn and maintain in the part of the codebase where most of the remaining work lives —
M4 through M11 are almost entirely server-side.

The obvious move, "drop Kotlin entirely", is not available. [ADR-0002](0002-kotlin-multiplatform-core.md)
exists because the engines must run **both** on the device (offline, in the browser) and on the
server (as the verifier), from one source, so that client and server can never disagree on a number
shown to a homeowner and later defended in a QualiPAC audit. Java has no responsible compile-to-JS
path: TeaVM is niche, J2CL is Bazel-shaped and thinly documented, GWT is legacy. Removing Kotlin
would mean maintaining two implementations of every formula in two languages, forever, converting a
compile-time guarantee into permanent test discipline.

Timing mattered: at the point this was decided the repository held 470 lines of Kotlin across 10
files, **none of it domain logic**. The same change after M2 (engines) and M7 (the PWA bridge) would
be several times the work and would carry a real risk of introducing a numerical discrepancy while
moving code.

## Decision

Draw the language boundary at the module edge:

| Module | Language | Why |
|---|---|---|
| `core` | Kotlin Multiplatform | The only place where compiling one source to JVM **and** JS is required |
| `server` | **Java 21** + Spring Boot 3 | Where most remaining work lives and where the team is strongest |
| `web` | TypeScript + React | Unchanged |

A `verifyNoKotlinInServer` check runs as part of `:server:check` and fails the build if any `.kt`
file appears under `server/`, so the boundary is enforced rather than remembered.

## Consequences

- The single-source-of-truth guarantee survives intact. Nothing about ADR-0002 changes.
- Milestones M4–M11 — persistence, REST, PDF, sync, media, auth, hardening — are Java.
- **M1, M2 and M3 remain Kotlin**, because they are the domain model and the two engines, which live
  in `core` by construction. Three of thirteen milestones are Kotlin work; this is the cost of the
  arrangement and was accepted knowingly.
- `:server` no longer needs the `kotlin-jvm` or `kotlin-spring` plugins; both were removed from the
  version catalog under [ADR-0008](0008-catalog-lists-only-consumed-dependencies.md).
- Java 21 pattern matching reads well against the sealed hierarchies the core is expected to expose
  (the dimensioning outcome type, quote status).

### The core's public API is now constrained by Java interop

This is the real, ongoing cost, and it lands on M1. `@JvmStatic` and `@JvmName` **cannot** be used
to smooth it over: they are JVM-only annotations and do not resolve in `commonMain`. The constraint
must therefore be met by API design, and it is pinned by `CoreInteropTest` in `:server`:

- **No `value class` in public signatures.** JVM names are mangled and effectively un-callable from
  Java. This directly overrides the natural Kotlin choice for M1-01's unit types (`PowerKw`,
  `MoneyEur`, …) — use plain data classes.
- **Prefer classes and interfaces over `object` singletons** on the public surface. Java reaches a
  Kotlin `object` only as `Xxx.INSTANCE.member()`. Engines are classes behind ports and are
  injected, so this costs nothing in practice.
- **No default arguments** on public functions; Java sees only the full-arity overload.
- Sealed hierarchies and data classes are fine.

## Alternatives considered

**Drop Kotlin entirely; Java server + a second TypeScript engine, bound by the shared golden-vector
corpus and differential testing.** Genuinely viable — the vector corpus is language-agnostic plain
text by design, so it would bind a Java engine to a TS engine as readily as it binds two Kotlin
targets, and barème churn is *data* (rule packs), which keeps the duplicated logic bounded and
stable. Rejected because it trades a structural guarantee for permanent discipline on the one part
of the system with legal weight, and the money-rounding seam between `BigDecimal` and JS numbers is
an unforced risk.

**Java everywhere with server-side-only calculation.** Rejected outright: it destroys the wedge. The
installer works in a cellar with no network and the 15-minute on-site devis is the product.

**Java compiled to JS via TeaVM or J2CL.** Rejected: exotic toolchain and poor debugging on the code
path that produces numbers quoted to clients and defended in audits.
