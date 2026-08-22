# ADR-0009 — Golden vectors are embedded, append-only fixtures

- **Status:** accepted
- **Date:** 2026-08-11

## Context

[ADR-0002](0002-kotlin-multiplatform-core.md) guarantees that client and server compute identically
because they share one source. That guarantee needs evidence, and the evidence has to run on both
targets over the same expectations — otherwise "client computes, server verifies" is an intention
rather than an enforced property.

Two practical constraints shape the design. `:core:jsTest` runs in a **browser** under Karma: there
is no filesystem, and Kotlin Multiplatform has no common resource-loading API, so fixtures cannot
simply be read as test resources. And the fixtures will eventually encode thermal coefficients and
barème values that a **domain reviewer** — not a developer — must be able to check against an
official source.

## Decision

Vectors are authored as plain `.vectors` data files under `core/src/commonTest/vectors/`. A Gradle
task embeds them into a generated `commonTest` source file, so one fixture set runs byte-identically
on the JVM and in the browser.

Vectors are **append-only**: once published, a vector is never edited and never deleted. A changed
result means the barème or the method changed, which is a *new* vector with a new id; the old one
stays, describing the old rule version.

Every vector must carry a `source` — a citation, or the literal `SOURCE_TBD` with a reason. The
suite enforces this, enforces unique ids, and fails when no vectors are found so it cannot pass
vacuously.

## Consequences

- A formula that behaves differently on the installer's phone than on the server fails in CI, rather
  than surfacing as a divergence flag in front of a homeowner.
- Authoring stays reviewable: adding a vector is a readable diff in a data file, not escaped Kotlin.
- Append-only makes the corpus a historical record, which is what supports reproducing a
  three-year-old devis against the rules that applied to it.
- The mandatory `source` field operationalises the standing rule that an unsourced number is never
  presented as authoritative — the failure mode this project can least afford.
- Cost: a codegen step in the build, and generated output that must not depend on filesystem
  ordering (files are sorted).
- Cost: the corpus only grows. That is intended, and it is cheap — these are small text fixtures.

## Alternatives considered

**Load the vectors as test resources.** The obvious approach, and unavailable: no filesystem in the
browser test target, no common resource API in KMP.

**Write vectors as Kotlin code in `commonTest`.** Works on both targets with no codegen, but makes
every fixture a code change and puts barème values behind Kotlin syntax, where the person best
placed to verify them cannot comfortably read them.

**Allow editing a vector when a barème changes.** Rejected: it would erase the record of what the
engine was agreed to produce at the time, which is exactly what legal reproducibility depends on.
