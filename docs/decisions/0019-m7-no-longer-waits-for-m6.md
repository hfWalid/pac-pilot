# 19. M7 no longer waits for M6, and the pitch still does

**Status:** accepted
**Date:** 2026-08-22
**Deciders:** operator
**Settles:** open question #2 in [README](README.md), raised on [PAC-8](https://atlasiam.atlassian.net/browse/PAC-8) and decided on [PAC-68](https://atlasiam.atlassian.net/browse/PAC-68)
**Relates to:** [ADR-0017](0017-no-provisional-rule-pack-on-the-server.md)

---

## Context

`DELIVERY-PLAN` §4's dependency graph runs `M6 → M7`. That puts a **human-gated data task** —
encoding and verifying real MaPrimeRénov' and CEE barèmes (PAC-75) — directly on the critical path
to the first end-to-end offline demo.

The graph and the epic text already disagree: the graph shows the dependency while PAC-8's own
description argues it is unnecessary, because the PWA only needs the M3 sample pack to be built and
tested. Whichever way this goes, that disagreement should not survive it.

## Decision

**M7 depends on M4, not M6.** The two epics run in parallel.

## Reasoning

**What M7 actually builds is the offline mechanism**: the service worker, the IndexedDB store, the
outbox, the JS core bridge, and the pre-visit screens. Not one of those needs a real barème. Its
Definition of Done — *"full pre-visit flow completes in airplane mode"* — is a statement about
connectivity, not about the accuracy of an aid figure.

**The refusal path is not a workaround; it is the state the PWA must handle anyway.**
[ADR-0017](0017-no-provisional-rule-pack-on-the-server.md) means no barème is published today, so
`AidsOutcome.NoPackPublished` is the *current production behaviour*, not a test condition. A PWA that
could not display that state honestly would be incomplete regardless of when M6 lands. Building M7
first forces that screen to exist, which is the right pressure.

**The mistaken-for-real risk is already mitigated in the model rather than by scheduling.** The M3
sample pack is `SOURCE_TBD` throughout, reports `isProvisional`, and lives in `commonTest` so it
cannot ship. Nothing about the ordering of epics changes that.

**And the pipeline benefits from landing early.** PRODUCT-VIEWS #11 puts *"barème churn outpaces
pack pipeline"* as the second-highest risk on the map. That argues for a pipeline that is built and
routinely runnable well before the first barème update arrives — which does not require it to gate
anything.

## The part that does not change, and this is the honest half

**The pitch still waits for PAC-75.** The offline flow's value is the reste-à-charge shown live to a
homeowner, and a synthetic figure demonstrates the *mechanism*, not the *pitch*. Re-parenting does
not make the pitch demoable earlier; it makes the mechanism demoable earlier. Those are different
things and conflating them would be the way to mislead oneself about progress.

So: **do not demo to an artisan on synthetic aids and read their reaction as signal.** What they
would be reacting to is a number the product does not yet stand behind. The field interviews that
`products/pac` is blocked on need real barèmes, and PAC-75 is what unblocks them.

## Consequences

- `DELIVERY-PLAN` §4's graph is updated: `M4 → M7`, and M6 parallel with M5.
- Open question #2 in `docs/decisions/README.md` is struck.
- M6 keeps its own schedule and its own ⚑ gate. **M6 cannot close until PAC-75 closes**, and PAC-75
  is a human verification task — the same boundary as the M2 method gate: an agent can encode what
  it is told and cross-check arithmetic; it cannot decide that a plafond is right.
- M6-06 (device pull, cache and verify) defines its contract here and is **implemented at M7**, since
  it is IndexedDB work in the PWA. That was already true in PAC-73's own text; this makes it the plan.

## Alternatives considered

**Leave `M6 → M7` in place.** The argument for it is real: nobody can mistake a demo for a working
product if the demo never runs on anything but verified barèmes. Rejected because the protection is
already structural — the sample pack cannot ship — and the cost is putting a data-verification task
between the product and its first honest end-to-end test of the thing the whole architecture exists
for, which is offline computation.

**Re-parent, and also allow demos on the sample pack.** Rejected. Re-parenting the *build* is sound;
licensing a *demo* on synthetic aid figures is not, and the two are easy to slide between. Recorded
above as an explicit prohibition rather than left to judgement.
