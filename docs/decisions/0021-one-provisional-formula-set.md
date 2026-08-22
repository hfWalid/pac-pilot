# 21. One provisional formula set, in `commonMain`

**Status:** accepted
**Date:** 2026-08-22
**Deciders:** operator
**Supersedes:** the placement half of M2-07; keeps its reasoning
**Relates to:** [ADR-0015](0015-server-boots-in-indicative-mode.md), PAC-40, PAC-42

---

## Context

M2-07 put `ProvisionalFormulaSet` in `commonTest` so that shipping it was **impossible by
construction** rather than discouraged by a guard. That was right at the time: nothing outside the
tests needed it, and a placeholder on the production classpath is exactly the artefact that quietly
becomes authoritative.

Its own comment predicted the pressure, precisely:

> *"`:server` at M4 and the PWA at M7 will have nothing to boot against and will each need a formula
> set of their own."*

Both have now arrived. [ADR-0015](0015-server-boots-in-indicative-mode.md) gave `:server` a Java
mirror. M7 needs one for the JS target. That would be **three copies of the same deliberately
non-physical numbers**, in three languages, each verified separately against the same golden vectors.

## Decision

**One `ProvisionalFormulaSet`, in `commonMain`.** The server's Java mirror is deleted; the JS target
uses the same definition.

## Why the original reasoning now points the other way

M2-07's concern was never the file's location — it was that a placeholder could be mistaken for a
method. Three hand-maintained copies serve that concern **worse**, not better:

- **Drift becomes possible, and drift is the failure mode that matters.** Three tables of magic
  numbers agree only as long as three people keep them agreeing. A server that computed 19,032 W
  where the tablet computed 19,031 W would surface as a divergence flag in front of a homeowner —
  which is precisely the thing the one-source-two-targets bet exists to prevent.
- **Each copy needed its own binding test.** `ProvisionalMethodMatchesCoreGoldenVectorsTest` existed
  only to check the Java mirror reproduced the Kotlin one. One definition needs no such test, and the
  core's own golden vectors already cover it on both targets.
- **The protection was never the placement anyway.** What stops these numbers being trusted is that
  they are visibly absurd — U-values that *rise* with newer construction, air's heat capacity at
  1.000 — and that every result they produce reports `INDICATIVE`. Both of those travel with the
  class wherever it lives.

## What replaces "impossible by construction"

The four safeguards ADR-0015 established, now applied once instead of twice:

1. **No default.** Nothing wires this set automatically. The server needs
   `pacpilot.dimensioning.method=indicative-provisional`; the PWA calls
   `installProvisionalFormulaSet()` by name. A deployment that chose nothing computes nothing.
2. **It announces itself.** The server logs a `WARN` banner; the PWA shows a degraded-mode banner.
3. **Every result says so.** `Confidence.INDICATIVE` is derived from whether the applied coefficients
   are sourced — nothing special-cases it, and this set cannot produce anything else.
4. **The name is the warning.** `ProvisionalFormulaSet`, `installProvisionalFormulaSet`,
   `indicative-provisional`. Nothing here reads as a method.

## Consequences

- One definition, verified once, on both targets, by the golden vectors that already exist.
- `:server`'s Java mirror and its binding test are deleted — roughly 300 lines whose only job was
  keeping a duplicate honest.
- **`:core` now ships a placeholder on its production classpath, which M2-07 refused.** That is the
  cost, stated plainly. It is accepted because the alternative is three placeholders, and because
  every safeguard that made ADR-0015 tolerable applies here unchanged.
- **When PAC-42 closes, this class is deleted rather than edited.** A validated method arrives as its
  own implementation; leaving this one selectable afterwards would recreate exactly the risk M2-07
  named.

## The better answer, named and deferred

`CLAUDE.md` §4.4 already says what the right end state is: *"Each MaPrimeRénov'/CEE/TVA/**thermal-
coefficient** version is a signed artifact on the CDN."* **Thermal coefficients were always meant to
be packs**, distributed and resolved by date exactly as barèmes are — which would mean neither the
server nor the PWA holds a formula set at all, and the ⚑ gate publishes one instead of landing code.

That is the design this should converge on. It is not done here because M6's pipeline publishes
barème packs only, and extending it to formula sets is its own piece of work with its own gate.
**Recorded so it is a deferred decision rather than an oversight** — and so that when PAC-42 closes,
whoever lands the validated method asks whether it should be a pack before it becomes a class.

## Alternatives considered

**Keep `commonTest` and add a JS mirror.** Three copies. Rejected above.

**Keep `commonTest` and have the PWA fetch the formula set from the server.** Rejected: it puts a
network call in front of the one thing that must work in a cellar with no signal.

**Ship nothing and let the PWA refuse to compute until PAC-42 closes.** Coherent, and rejected for
the same reason ADR-0015 rejected it server-side: the offline bet is the keystone of this product
(ARCHITECTURE #3), and it cannot be demonstrated or tested at all without *some* method. A product
whose central claim is untestable until a human gate closes is a product nobody can de-risk.
