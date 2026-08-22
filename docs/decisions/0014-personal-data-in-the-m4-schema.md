# ADR-0014 — What the M4 schema stores about people, for how long, and what deletion means

- **Status:** accepted
- **Date:** 2026-08-22
- **Settles:** open question 5 in [README](README.md), raised on [PAC-6](https://atlasiam.atlassian.net/browse/PAC-6) and decided on [PAC-51](https://atlasiam.atlassian.net/browse/PAC-51)
- **Relates to:** M3-08 ([PAC-50](https://atlasiam.atlassian.net/browse/PAC-50)), which kept the income decile out of every *rendered* surface. This decides whether it is *written down*.

## Context

The RGPD operations review is scheduled at M11, but M4 designs the tables that hold client names,
addresses and **fiscal income deciles**. "By design" (`CLAUDE.md` §4.6) means before the migration,
not before go-live — and Flyway is forward-only, so a column created without a retention answer is
paid for later with a migration that rewrites data rather than one that creates it.

## Decision

### 1. Every personal column answers three questions before it exists

No column holding personal data is created unless its lawful basis, retention period and deletion
behaviour are all named. The M4 schema's personal columns, in full:

| Table.column | What it is | Lawful basis | Retention | On erasure |
|---|---|---|---|---|
| `client.first_name`, `last_name` | Identity | Contract | Life of the relationship + 10 y from the last validated study | Anonymise |
| `client.email`, `phone` | Contact | Contract | Life of the relationship | **Hard delete** |
| `site.address_line`, `postcode`, `commune` | Where the work is | Contract | With the study that used it | Anonymise |
| `site.latitude`, `longitude` | Opportunistic BAN geocode | Legitimate interest | With the site | **Hard delete** |
| `site.*` dwelling characteristics | Surface, period, insulation, emitters | Contract | With the study | Retain, de-linked |
| `dimensioning.inputs_*` | The snapshot a study was computed from | Legal obligation | 10 y from validation | Retain, de-linked |
| `quote.income_decile` | Fiscal income band, 1–10 | Legal obligation | 10 y from validation | Retain, de-linked |
| `installer.*` | The operator's own account | Contract | Life of the account | Out of scope — not a data subject here |

### 2. The income decile is stored, and only as a decile

**Stored**, because the server verifier recomputes the aids from the stored inputs plus the recorded
pack version and asserts equality (`CLAUDE.md` §4.2). A resolved aid with no recorded decile cannot
be recomputed, which would break the verification path at M4-07, the divergence check at M8, and the
reproducibility guarantee the whole pack architecture exists for.

**Only as a decile.** The band is the minimisation: the product never asks for, receives or stores a
revenue figure, a tax notice, a household composition or a *numéro fiscal*. One integer in 1–10 is
the least data that still computes the aid. `IncomeDecile` already refuses to render itself (M3-08),
so it is minimised in transit as well as at rest.

### 3. Erasure is anonymisation where a record has legal weight, hard delete where it does not

A validated dimensioning and the devis built on it are **evidence**. They are what an installer
shows an auditor, an insurer, or a client who disputes a figure — the décennale exposure the product
exists to cover. Hard-deleting them on request would destroy the artisan's own defence, and
retention for a legal obligation is a recognised limit on the right to erasure.

So erasure of a client:
- **severs the identity** — names anonymised, email and phone hard-deleted, geocode hard-deleted;
- **retains the study and the devis, de-linked**, carrying dwelling characteristics and the income
  decile but no longer reachable from a named person.

A client who was never party to a validated study has no such weight, and is hard-deleted entirely.

### 4. The schema supports this from the first migration

Every table holding personal data carries `anonymised_at timestamptz null` from creation, not from a
later migration. Erasure is a state the schema can express on day one.

## Consequences

- M4-03 and M4-10 have concrete answers and need not ask again.
- **The 10-year figure is the one number here that is not ours to set.** It is chosen to align with
  the décennale window that is the product's candidate forcing function, and it is recorded as a
  *position to be confirmed*, not as legal advice. `TODO(unverified)`: confirm at M11 with a DPO,
  together with the lawful-basis column above. The schema is designed so a shorter or longer period
  is a configuration change and a retention job, not a migration.
- Anonymisation must be irreversible to count as erasure. No shadow copy, no soft-deleted row still
  carrying the name, no name surviving in an audit log. The retention job is M11's; the columns it
  needs exist from M4.
- Backups fall outside this ADR and are M11's problem, but the position is recorded now: an erasure
  that a restore silently undoes is not an erasure.

## Alternatives considered

**Store nothing but the resolved aid amount.** The strongest minimisation available, and rejected
because it breaks verification: the server could no longer recompute what the client computed, so
the divergence check at M8 would have nothing to compare and a devis would stop being reproducible.
Reproducibility is the product.

**Hard-delete everything on request, without exception.** Rejected. It reads as the more
privacy-respecting choice and is in fact worse for both parties: it destroys the installer's
evidence for a job they remain liable for, and it is not what a legal-obligation retention basis
requires.

**Defer the whole question to M11 as originally planned.** Rejected — that is the open question this
ADR closes. Every column above would have been created without an answer, and forward-only
migrations make that a rewrite.
