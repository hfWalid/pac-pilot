# 16. Cross-context foreign keys are allowed, and extraction pays for it

**Status:** accepted
**Date:** 2026-08-22
**Deciders:** operator
**Relates to:** [ADR-0004](0004-modular-monolith.md), [ADR-0013](0013-minimal-installer-identity-at-m4.md), PAC-52, PAC-54

---

## Context

M4 writes the first migrations that reference another context's tables. `dossier_client.installer_id`
points at `identity_installer` (V3); `dimensioning_study.site_id` points at `dossier_site` and
`validated_by` at `identity_installer` (V4). Every remaining M4 ticket adds more.

Two rules are in tension, and both are real:

- `CLAUDE.md` §4.7 and [ADR-0004](0004-modular-monolith.md) require **hard internal walls**, with any
  context extractable later without a rewrite. A foreign key is precisely the thing that makes
  extraction a data migration rather than a code change.
- The same documents require **one deployable, one database**, and explicitly exclude microservices
  (§12). There is no second database for a constraint to fail to reach.

`BoundedContextRulesTest` already enforces the code-level wall: no context reaches into another's
internals, and no entity maps a table its context does not own. This ADR settles the *data*-level
question, which bytecode rules cannot see.

## Decision

**Cross-context foreign keys are allowed.** A context may declare a foreign key onto another
context's table.

The code-level wall is unaffected: reads still go through the owning context's exposed surface, and
an entity still may not map a table another context owns. What is permitted is the constraint alone.

## Consequences

**What this buys.** The database refuses a study attributed to an installer who does not exist, or a
client on a site that was never recorded. In a product whose whole claim is that a devis is
reproducible and defensible years later, a dangling reference is not a tidiness problem — it is a
hole in the audit chain, and it would be discovered by an auditor rather than by a test.

Application-level integrity checks are the alternative, and with a solo operator they are the kind
of thing that gets written for the first three references and forgotten for the fourth.

**What it costs, stated plainly.** Extracting a context stops being a code change. It becomes:
drop the constraint, replicate or denormalise the referenced data, and reintroduce the check in the
application. That is bounded, well-understood work, but it is a data migration and it is not free.

**When this becomes the wrong call.** If a context is ever genuinely extracted — or if a second
datastore appears for any reason — this decision is what has to be unwound first. Nothing in the
plan through M11 does either.

**On-delete behaviour is decided per reference, not here.** `dimensioning_assumption` cascades from
its study because it is a component of it. No cross-*context* reference cascades: deleting a site
must not silently destroy the studies that constitute the evidence for a devis. Those are refused
and handled by the erasure path in [ADR-0014](0014-personal-data-in-the-m4-schema.md), which
anonymises rather than deletes.

## Alternatives considered

**No cross-context foreign keys.** Keeps extraction free. Rejected because it pays a certain,
permanent integrity cost for an extraction that is not planned in any epic through M11, and because
the mechanism that would replace it — application-level checks — is exactly the discipline a
one-person team is worst at sustaining.

**Foreign keys only within a context, plus a scheduled integrity job.** Rejected as the worst of
both: it accepts the dangling reference and then spends effort detecting it after the fact, which
in this product means detecting it after a devis has already been issued.
