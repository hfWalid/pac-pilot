# Personal data in the schema

**Purpose:** the M11 RGPD review starts from this list rather than from a survey of the schema.

**Authority:** [ADR-0014](decisions/0014-personal-data-in-the-m4-schema.md) decides what is stored,
on what basis, for how long, and what happens on erasure. This file is the operational index of that
decision — when the two disagree, the ADR is right and this file is stale.

**Kept honest by test.** `PersonalDataErasureTest` sweeps *every* text column of *every* table
discovered from `information_schema`, not a list written by hand — so a column added by a later
migration is searched automatically. A hand-maintained list is exactly what misses the denormalised
copy.

---

## Columns holding personal data

| Table.column | What it is | Lawful basis | Retention | On erasure |
|---|---|---|---|---|
| `dossier_client.first_name`, `last_name` | Identity | Contract | Relationship + 10 y from last validated study | Anonymised to `(efface)` |
| `dossier_client.email`, `phone` | Contact | Contract | Life of the relationship | **Hard delete** |
| `dossier_site.address_line`, `postcode`, `commune` | Where the work is | Contract | With the study that used it | Anonymised to `(efface)` |
| `dossier_site.latitude`, `longitude` | Opportunistic BAN geocode | Legitimate interest | With the site | **Hard delete** |
| `quoting_quote.income_decile` | Fiscal income band, 1–10 | Legal obligation | 10 y from validation | Retained, de-linked |

## Columns that are *not* personal data, and why the distinction matters

| Column | Why it stays |
|---|---|
| `dossier_site.departement_code` | One of ~100 areas. Not identifying, and the study needs it to resolve the climate row it was computed against. Coarser than the postcode it replaces. |
| `dossier_site` dwelling characteristics | A surface and a construction period describe a building, not a person. |
| `dimensioning_study.*` | The snapshot a study was computed from. Evidence, de-linked once the client is anonymised. |
| `identity_installer.*` | The operator's own account, not a data subject in this system (ADR-0014). |

## What erasure actually does

`ErasePersonalData.erase(clientId)` — the identity is severed, the arithmetic survives.

The two pull in opposite directions and the tension is real: a devis must reproduce years later,
and a client may ask to be forgotten. A validated study and the devis built on it are **evidence** —
what an artisan shows an auditor, an insurer, or a client disputing a figure. Hard-deleting them on
request would destroy the artisan's own défense.

So erasure severs the identity and keeps the figures. A devis that reproduces its arithmetic without
naming anyone is still an audit artefact.

It reaches every site the client owns, not just the client row.

**Irreversible by construction.** `Client.anonymised` and `Site.anonymised` return new records that
never held the previous values — there is no shadow copy to leak and nothing for a later "undo" to
restore.

## Open for M11

- **The 10-year retention figure is not this repository's to set.** It is aligned to the décennale
  window and marked `TODO(unverified)` in ADR-0014 — a position to confirm with a DPO, together with
  the lawful-basis column. The schema is built so a different period is a configuration change and a
  retention job, not a migration.
- **The retention job itself does not exist.** The columns it needs do, and
  `dossier_client_pending_erasure` indexes the clients it would scan.
- **Backups.** An erasure a restore silently undoes is not an erasure. Out of scope here, named so it
  is not forgotten.
