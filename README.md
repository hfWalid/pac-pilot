# pac-pilot

Mobile-first, offline-capable, local-first SaaS for French heat-pump installers (artisans QualiPAC).

**V1 wedge:** during the mandatory on-site pre-visit, the installer produces in ~15 minutes a simplified
heat-loss dimensioning, an assisted PAC selection, and a devis with public aids pre-computed and
reste-à-charge shown to the client — fully offline, synced and server-verified later.

## Documentation

The Markdown docs are the source of truth; there is no Confluence.

> **They are not in this repository yet.** Until **M0-09** brings them into `docs/` (together with
> `docs/decisions/`, one ADR per locked decision), they live outside the repo at
> `Developer/PAC/`. A fresh clone does not contain them — ask for them, and do not
> treat anything in this README as a substitute.

Read in this order once they land:

| # | Doc | What it answers |
|---|-----|-----------------|
| 1 | `CLAUDE.md` | What we are building, and what we have forbidden ourselves from building |
| 2 | `docs/PRODUCT-VIEWS.md` | Why a feature exists and what it changes for the artisan |
| 3 | `docs/ARCHITECTURE.md` | How the system is shaped and why each boundary is where it is |
| 4 | `docs/DELIVERY-PLAN.md` | Where your code goes and which epic owns it |

## Modules

| Module | Purpose | Added by |
|--------|---------|----------|
| `core` | Kotlin Multiplatform — domain model + engines, compiled to **JVM** (server verifier) and **JS** (PWA) | M0-02 |
| `server` | Kotlin / Spring Boot 3 modular monolith | M0-03 |
| `web` | React + TypeScript PWA — the only client | M0-04 |
| `rulepacks` | Versioned, checksummed barème artifacts | M6 |

> "Two targets" means the **calculation core compiled twice**, not a cross-platform mobile app.
> There is one client only: the PWA. There is no native build.

## Status — M0, scaffold only

Not deployable and **not offline-capable**. The service worker registers but caches nothing; the
offline strategy, the local store and the outbox all arrive at **M7**. Until then this must not be
deployed, installed for a pilot installer, or presented as working product.

Still open in M0: Postgres + Flyway (M0-05), the ArchUnit purity rule (M0-06), the golden-vector
harness (M0-07), CI (M0-08), docs and ADRs (M0-09).

## Build

Requires JDK 21 and Node — the version pinned in [`.nvmrc`](.nvmrc), enforced by the build.
Run `nvm use` first. Gradle comes from the wrapper; no local install needed.

```bash
./gradlew build
```

One Node version serves the whole build: Gradle provisions it for Kotlin/JS, and `:web` validates
the ambient toolchain against the same pin so the golden vectors and the shipped bundle cannot end
up on different runtimes.

Dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). No module build
file may declare a version inline.

## Working convention

- One task = one branch off `develop`, named for the task id (`M0-01`).
- Commits: `[M0][M0-01] <imperative summary>`.
- Merged back into `develop` with `--no-ff`.
- Golden vectors are append-only from M2 onward — a published vector is never edited.
- Never invent a thermal coefficient, U-value or barème. Unsourced numbers are marked `SOURCE_TBD`,
  surfaced, and never presented as authoritative.
