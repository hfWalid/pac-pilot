# ADR-0007 — One pinned Node LTS version across the whole build

- **Status:** accepted
- **Date:** 2026-08-11

## Context

Two independent Node runtimes exist in this build. Kotlin/JS provisions its own to run `jsTest` under
Karma, and `:web` shells out to whatever `npm` is on `PATH` for the Vite build. Left alone they were
genuinely different majors — measured at 24.10.0 for Kotlin/JS and 26.7.0 ambient — meaning the JS
that executes the golden vectors was not the JS the PWA was bundled with. That silently weakens the
central guarantee of [ADR-0002](0002-kotlin-multiplatform-core.md).

A first attempt pinned only the root project's `NodeJsEnvSpec`. It did not work: Kotlin Multiplatform
installs a spec on **every** project carrying a JS target, so `:core` — the project that actually
compiles and tests Kotlin/JS — kept the plugin default. The mistake was invisible because the root
spec *was* pinned and did download the requested version, so the evidence looked right.

## Decision

`.nvmrc` holds one version and is the single pin. Gradle applies it to the `NodeJsEnvSpec` of **all**
projects, and `:web:verifyNodeToolchain` fails the build when the ambient Node does not match, or
when npm is below the `engines.npm` floor.

The pinned version tracks the **LTS** line, not Current.

## Consequences

- One runtime for the whole build; the golden vectors and the shipped bundle cannot diverge.
- A developer on the wrong Node gets an actionable failure naming both versions, rather than a
  confusing downstream error.
- LTS means a long support window and no breaking changes mid-line, which matters for a project
  whose stated priorities are correctness and reproducibility.
- `nvm use` resolves to the LTS line by default, so the pin costs no day-to-day friction.
- Cost: bumping Node is a deliberate act touching `.nvmrc`, and CI must re-provision.

## Alternatives considered

**Pin to Current (26.7.0).** This was the first choice and it was wrong twice over. The stated
justification — that it matched the installed toolchain — came from a non-interactive shell that
never loaded nvm and fell through to a Homebrew install; the actual working default was the LTS line.
And Current carries breaking changes and a short support window, which is the wrong trade here.

**Make `:web` use the Kotlin-provisioned Node.** Genuinely the strongest option — one runtime, zero
developer setup — but it requires computing the provisioned binary's path across operating systems.
Deferred as more fragility than the problem warrants now.

**Leave Node ambient and document the expectation.** Rejected: it is exactly what produced the
two-major split, and documentation does not fail a build.
