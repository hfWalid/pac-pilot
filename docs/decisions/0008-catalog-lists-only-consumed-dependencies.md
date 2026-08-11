# ADR-0008 — The version catalog lists only consumed dependencies

- **Status:** accepted
- **Date:** 2026-08-11

## Context

M0-01 was specified to pin a list of dependencies up front, including some no module used yet. As
delivered it pinned ArchUnit, which nothing consumed, and missed Flyway and JUnit 5 — and the gap
went unnoticed until review, precisely because nothing built against the entries either way.

A pinned-but-unused entry is dead configuration. No module resolves it, so nothing proves the
version exists, resolves, or works alongside everything else. It reads as a decision while carrying
none of a decision's verification.

## Decision

`gradle/libs.versions.toml` lists only what is actually consumed by a module. An entry arrives with
the task that first needs it, in the same commit that uses it.

## Consequences

- Every catalog entry is exercised by a build, so a broken or conflicting version fails immediately
  rather than lying dormant.
- The catalog stays an accurate inventory of the project's real dependencies.
- Cost: a small edit accompanies each new dependency — ArchUnit returned at M0-06, Flyway and the
  Postgres driver at M0-05, and kotlinx-datetime is expected at M1-03.
- JUnit 5 needs no entry on `:server`: the Spring Boot BOM manages it. `:core` has no BOM, so it
  pins the JUnit platform itself — an illustration of the rule rather than an exception to it.

## Alternatives considered

**Pin everything the roadmap will eventually need.** Rejected: it is the practice that produced the
unused entry, and it invites versions that were never resolved together.

**No catalog; declare versions in module build files.** Rejected: four modules would drift, which is
the problem the catalog exists to prevent.
