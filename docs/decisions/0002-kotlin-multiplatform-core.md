# ADR-0002 — One calculation core, compiled to JVM and JS

- **Status:** accepted
- **Date:** 2026-08-11

## Context

The installer computes a dimensioning and a reste-à-charge **offline, on a device**, and shows the
result to a homeowner on the spot. The server later recomputes the same result from the stored
inputs and rule-pack version to verify it (CLAUDE.md §4.2).

Two implementations of the same formulas — one in TypeScript for the browser, one in Kotlin or Java
for the server — would drift. Not immediately, but on the third barème change, in an edge case
nobody wrote a test for. The consequence is not a stack trace: it is a divergence flag raised after
an installer has already quoted a number to a client.

## Decision

The domain model and both engines are written once in Kotlin Multiplatform `commonMain` and compiled
to two targets: **JVM** (consumed by `:server` as the verifier) and **JS** (consumed by `:web` for
on-device computation). A shared suite of golden vectors runs on both targets and is the correctness
contract binding them ([ADR-0009](0009-golden-vectors-are-append-only.md)).

`commonMain` stays framework-free — no Spring, no JPA, no serialization framework — enforced by
ArchUnit rather than by convention.

## Consequences

- Client and server cannot disagree on a formula, because there is only one formula.
- The engines stay embeddable: shipping them inside a grossiste portal or fabricant configurator
  later is a new adapter behind an existing port, not a rewrite (CLAUDE.md §3).
- Purity is load-bearing, not stylistic: a Spring or JPA import does not merely offend the hexagon,
  it breaks the JS target outright.
- Cost: two compilations, so the build is slower, and the JS test path runs in a browser under Karma
  — which is why golden vectors are embedded rather than read as resources.
- Cost: Kotlin types do not all cross cleanly into TypeScript; the JS target needs a thin `@JsExport`
  façade and `:web` needs a single bridge module rather than importing the artifact everywhere.

## Alternatives considered

**Server-only calculation.** Rejected outright: it defeats the product. The installer works in a
cellar with no network, and the 15-minute on-site devis is the entire wedge.

**Duplicate implementations, kept in step by a shared test corpus.** Rejected: the corpus would
catch drift only where a vector exists, and the failure mode is silent and expensive.

**WebAssembly compiled from the JVM core.** Rejected as premature — heavier tooling, worse debugging,
and no benefit over the JS target for arithmetic of this size.

> "Two targets" throughout the documentation means **the calculation core compiled twice**. It does
> not mean a cross-platform mobile app; see [ADR-0003](0003-pwa-is-the-only-client.md).
