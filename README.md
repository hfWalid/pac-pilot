# pac-pilot

Mobile-first, offline-capable, local-first SaaS for French heat-pump installers (artisans QualiPAC).

**V1 wedge:** during the mandatory on-site pre-visit, the installer produces in ~15 minutes a simplified
heat-loss dimensioning, an assisted PAC selection, and a devis with public aids pre-computed and
reste-à-charge shown to the client — fully offline, synced and server-verified later.

## Documentation

The Markdown docs are the source of truth; there is no Confluence. They are versioned here, next to
the code they describe. If a ticket and a doc disagree, the doc wins until the doc is updated — and
updating it is part of the ticket.

Read them in this order; each assumes the previous one. Budget ~45 minutes for all four.

| # | Doc | What it answers |
|---|-----|-----------------|
| 1 | [`CLAUDE.md`](CLAUDE.md) | What we are building, and what we have forbidden ourselves from building |
| 2 | [`docs/PRODUCT-VIEWS.md`](docs/PRODUCT-VIEWS.md) | Why a feature exists and what it changes for the artisan |
| 3 | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | How the system is shaped and why each boundary is where it is |
| 4 | [`docs/DELIVERY-PLAN.md`](docs/DELIVERY-PLAN.md) | Where your code goes and which epic owns it |

**Start with `CLAUDE.md`, always.** It is the persistent context file Claude Code loads before
acting and it doubles as the human brief. Its "Out — V1" and "Things NOT to assume" sections matter
as much as the scope itself: they record decisions already argued and settled. Do not reopen one in
a ticket without a decision record.

[`docs/decisions/`](docs/decisions/) holds one ADR per locked decision — the reasoning, not just the
verdict. Six questions raised in review remain **undecided** and are deliberately not written up as
ADRs; they are listed in [`docs/decisions/README.md`](docs/decisions/README.md).

Two of these carry direct implementation constraints rather than background: PRODUCT-VIEWS #5 (the
15-minute pre-visit, with a per-step time budget) and #9 (degraded-mode UX).

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

M0 is complete. CI is defined in `.github/workflows/ci.yml` but **has never executed** — there is no
git remote yet, so its first real run is still outstanding.

## Build

Requires JDK 21 and Node — the version pinned in [`.nvmrc`](.nvmrc), enforced by the build.
Run `nvm use` first. Gradle comes from the wrapper; no local install needed.

```bash
./gradlew build
```

One Node version serves the whole build: Gradle provisions it for Kotlin/JS, and `:web` validates
the ambient toolchain against the same pin so the golden vectors and the shipped bundle cannot end
up on different runtimes.

## Database

PostgreSQL runs in Docker; no local install and no manual setup.

```bash
docker compose up -d
```

Then `./gradlew :server:bootRun` — Flyway applies migrations at startup. Verify with:

```bash
docker compose exec db psql -U pacpilot -d pacpilot -c "select version, description, success from flyway_schema_history;"
```

Reset to an empty database with `docker compose down -v && docker compose up -d`.

### Adding a migration

Create `server/src/main/resources/db/migration/V<n>__<description>.sql`, next number in sequence.

- **Forward-only.** Never edit a migration once applied anywhere but your own machine — Flyway
  stores a checksum, and changing an applied file breaks every other environment.
- Reference data (climate zones, default U-values, the PAC catalog) is seeded by versioned
  migrations, never by hand.
- One migration, one coherent change, reviewed like code.

Migrations are verified against a real PostgreSQL of the same major version via Testcontainers.
Those tests skip automatically when no Docker daemon is present, so `./gradlew build` still works
without it — but they do run in CI, so a broken migration cannot merge.

Dependency versions live in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). No module build
file may declare a version inline.

## Working convention

- One task = one branch off `develop`, named for the task id (`M0-01`).
- Commits: `[M0][M0-01] <imperative summary>`.
- Merged back into `develop` with `--no-ff`.
- Golden vectors are append-only from M2 onward — a published vector is never edited.
- Never invent a thermal coefficient, U-value or barème. Unsourced numbers are marked `SOURCE_TBD`,
  surfaced, and never presented as authoritative.
