# ADR-0013 — A minimal `Installer` identity lands at M4; authentication stays at M10

- **Status:** accepted
- **Date:** 2026-08-22
- **Settles:** open question 4 in [README](README.md), raised on [PAC-6](https://atlasiam.atlassian.net/browse/PAC-6) and decided on [PAC-51](https://atlasiam.atlassian.net/browse/PAC-51)

## Context

`validatedBy` is the legal shield (`CLAUDE.md` §4.5). The product is an *aide à la décision*: every
computed result is a proposal until a qualified professional validates it, and the record of **who**
validated it is what separates a defensible study from a machine-generated number.

As planned, identity arrives at M10. Between M4 and M10 every `validatedBy` would therefore point at
a stub — an `InstallerId` referencing nothing. Four epics of persisted studies and quotes would
carry a validation act attributed to an installer the system cannot name, and M8's idempotent
ingestion needs a principal to attribute a change-log to well before M10 as well.

The narrow question the ticket posed: *is an `Installer` row without authentication useful, or is it
a table pretending to mean something?*

## Decision

Land a minimal `Installer` **account of record** at M4: an id, a display name, a SIRET and a
QualiPAC/RGE qualification reference, created by migration and seed rather than by signup. No
credentials, no sessions, no signup flow, no password column.

M10 then adds only what it is actually about: email/password and magic-link authentication, session
lifecycle, and the signup path. It does not introduce the concept of an installer, it introduces
proof that a request is from one.

## Consequences

- `validatedBy` references a real row from the first migration that persists a validation act. The
  audit chain has no four-epic hole.
- **The table is not pretending.** An `Installer` row asserts exactly one thing — this account of
  record exists and work is attributed to it — and it is true. What it does *not* assert is that a
  given HTTP request came from that installer; nothing before M10 may claim that, and the REST layer
  at M4-08 takes the installer as an explicit parameter rather than inferring one from a session
  that does not exist.
- The gap is therefore honest and narrow: **attribution without authentication.** Recorded here so
  M10 knows precisely what it is closing, and so no reviewer between now and then mistakes a
  populated `validated_by` for a proven one.
- A deployment before M10 cannot be exposed to the public internet. That is already true for other
  reasons and is not weakened here.
- `installer` gets no personal-data retention rule of its own beyond the operator's own account
  data — it is the business, not a client. See [ADR-0014](0014-personal-data-in-the-m4-schema.md).

## Alternatives considered

**Wait for M10.** Rejected. It puts a fiction into the audit chain — the one part of the schema whose
entire purpose is to be trustworthy years later — and it would be discovered at M8, when sync needs
a principal, as an unplanned dependency rather than a decision.

**Land full identity at M4.** Rejected as scope creep into an epic that is already the largest in the
plan, and `CLAUDE.md` §11 defers auth explicitly. Authentication has its own security surface —
session lifecycle, token storage, rotation, CSRF — that deserves the focused epic it already has.
