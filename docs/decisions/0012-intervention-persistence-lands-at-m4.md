# ADR-0012 — `Intervention` persistence lands at M4, not M7.5

- **Status:** accepted
- **Date:** 2026-08-22
- **Settles:** open question 3 in [README](README.md), raised on [PAC-6](https://atlasiam.atlassian.net/browse/PAC-6) and decided on [PAC-51](https://atlasiam.atlassian.net/browse/PAC-51)
- **Relates to:** [ADR-0004](0004-modular-monolith.md)

## Context

`DELIVERY-PLAN` §4 schedules the `Intervention` context at M7.5 — after the PWA — while M4 is
described as "migrations for **all** aggregates". Those two statements cannot both hold. As planned,
M7.5 would add a bounded context, a set of tables and a Flyway migration to a server backbone that
M4 declared finished two epics earlier, while also delivering the timeline UI, offline CRUD, local
filters and maintenance recurrence.

The domain question is already settled and is not reopened here: `Intervention` is server-side with
a client replica, it carries no calculation, and it therefore has no reason to live in the KMP core
(`DELIVERY-PLAN` §3). What was open is purely **when the tables appear**.

## Decision

The `interventions` context and its migrations land in **M4**.

M7.5 keeps everything that is genuinely PWA work — the timeline home screen, offline CRUD, the local
IndexedDB filters, opportunistic BAN geocoding, and the maintenance auto-recurrence on a completed
install — and adds no server context and no schema change.

## Consequences

- M4 grows by one context and one migration. It was already the largest epic in the plan; this makes
  it larger, and that cost is accepted here rather than discovered at M7.5.
- **The server backbone closes once.** "All aggregates are migrated" becomes true when M4 says it is,
  which is the property the rest of the plan assumes when it treats M4 as a foundation.
- M7.5 becomes a single-layer epic. An epic spanning core, server and web has three ways to be
  half-finished; this one now has one.
- The status enum stays open (`PLANNED → DONE | CANCELLED | RESCHEDULED | NO_SHOW`) so the V1.5
  booking path (`REQUESTED → CONFIRMED | DECLINED`) remains an addition rather than a migration of
  meaning. Landing the table earlier does not close that door — the column is text with a check
  constraint, not a Postgres enum type, precisely so a new member is not a schema change.
- `address_snapshot` is denormalised onto the intervention at creation, per `CLAUDE.md` §14, so a
  later `Site` edit does not rewrite where a visit was recorded as having happened.

## Alternatives considered

**Leave it at M7.5 as planned.** Rejected. The cost is not the work, it is that M4's Definition of
Done becomes untrue on the day it is signed off, and the plan's dependency graph treats M4 as
complete from that point onward. A foundation epic that is knowingly incomplete is worse than a
larger one.

**Model `Intervention` now, migrate at M7.5.** Rejected as the worst of both: the schema decision is
still deferred past the epic that owns schema, and Flyway is forward-only, so the deferral is paid
for with a migration that rewrites rather than creates.
