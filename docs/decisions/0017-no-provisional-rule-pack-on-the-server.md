# 17. No provisional rule pack on the server; the aids path refuses until M6

**Status:** accepted
**Date:** 2026-08-22
**Deciders:** operator
**Relates to:** [ADR-0015](0015-server-boots-in-indicative-mode.md), PAC-57, PAC-43, PAC-75

---

## Context

[ADR-0015](0015-server-boots-in-indicative-mode.md) let the server boot with a **provisional formula
set**, so the dimensioning engine can run before the method gate (PAC-42) closes. M4-07 asks for the
aids engine to be wired the same way, and the obvious move is to mirror the decision: ship a
provisional rule pack, mark it indicative, carry on.

The obvious move is wrong here, and the asymmetry is worth writing down because the next person will
ask why one placeholder was acceptable and the other was not.

## Decision

**The server ships no rule pack at all.** Its `RulePackRepository` resolves nothing until M6's
pipeline publishes real, human-verified barèmes, so every aids resolution returns
`AidsOutcome.NoPackPublished` for every date.

## Why this differs from ADR-0015

**A placeholder coefficient produces a wrong number. A placeholder barème produces a wrong *euro
amount*, and that is the most quotable figure the product makes.**

- A provisional heat load is 19,032 W. It is meaningless, it is marked `INDICATIVE`, and nobody
  reads it as a promise. Its failure mode is a badly sized machine, caught by the installer.
- A provisional MaPrimeRénov' figure is "vous toucherez 4 000 €". A homeowner remembers that
  sentence, repeats it, and plans around it. Its failure mode is a household that budgeted for money
  that was never coming — and the artisan who said it carries that conversation.

M3-01's ticket named this precisely: a sample pack "is exactly the artefact that quietly becomes
authoritative — someone wires it into a demo, the demo becomes a pilot, and a placeholder
MaPrimeRénov' figure is shown to a homeowner as what they will actually receive."

**And unlike the formula set, nothing is blocked by refusing.** There is no way to run a dimensioning
engine without coefficients — the engine *is* the arithmetic, so ADR-0015 had to choose between a
placeholder and a server that will not start. The aids engine has no such problem: it works
perfectly, and `AidsOutcome.NoPackPublished` is already a first-class, modelled outcome from M3-03,
built precisely because "a devis dated before the first pack, or inside a publication gap, must be
refused explicitly and not priced against the nearest pack."

So refusing is not a compromise here. It is the domain working as designed.

## Consequences

- The M4 pre-visit flow ends with an explicit refusal at the aids step, and M4-09 asserts that
  refusal rather than a number. That is the honest end-to-end state of the product today.
- A devis can still be produced; it carries no aid lines and its reste-à-charge equals the full TTC
  price. That is correct — no barème has been applied.
- The API must surface the refusal as a distinguishable state, never as "aids: 0 €". Zero is a
  claim about the household; a refusal is a statement about the system.
- **When M6 lands**, this ADR is superseded by a repository reading published packs. Nothing else
  changes shape: the engine, the port and the outcome type are already right.

## Alternatives considered

**Mirror ADR-0015 with a provisional pack, marked indicative.** Rejected for the reason above: the
blast radius of a placeholder euro amount is a homeowner's budget, not a test fixture, and the
safeguard that makes ADR-0015 tolerable — nothing is blocked without it — does not apply.

**Move the M3 sample packs from `commonTest` onto the production classpath.** Rejected outright.
M3-01 put them in `commonTest` so that shipping them is impossible by construction; undoing that to
make a demo work is precisely the sequence the ticket warned about.
