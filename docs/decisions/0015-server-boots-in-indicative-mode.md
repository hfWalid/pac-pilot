# ADR-0015 — The server boots without a validated method, in explicit indicative mode

- **Status:** accepted
- **Date:** 2026-08-22
- **Decided on:** [PAC-51](https://atlasiam.atlassian.net/browse/PAC-51)
- **Overrules:** the proposal in [`M2-GATE-WORKSHEET.md`](../M2-GATE-WORKSHEET.md) §2, that a build
  without a validated formula set should refuse to start
- **Relates to:** [PAC-42](https://atlasiam.atlassian.net/browse/PAC-42) (the ⚑ method gate, still open)

## Context

M2-07 put `ProvisionalFormulaSet` in `commonTest`, where shipping it is impossible by construction.
The worksheet recorded the consequence honestly and deferred the decision to exactly this moment:
`:server` at M4 has nothing to boot against while PAC-42 is open, and the worksheet's proposal was
that it should therefore **refuse to start** rather than compute with placeholders.

Refusing to start would leave M4 unable to meet its own Definition of Done — "the full API flow green
in integration tests, end to end" — because the dimensioning half of that flow could never run.

## Decision

The server boots with a provisional formula set, in an **explicit indicative mode** that cannot be
entered by accident.

Four properties make "explicit" mean something rather than being a comment in a config file:

1. **No default.** `pacpilot.dimensioning.method` has no fallback value. A server started without it
   fails at context refresh with a message naming PAC-42. Indicative mode is reached only by an
   operator who wrote `indicative-provisional` deliberately.
2. **It announces itself.** Startup logs a `WARN` banner stating that no validated method is in force,
   that every result is indicative, and that no output may be shown to a client.
3. **Every result already says so.** M2 built `Confidence.INDICATIVE` into `HeatLoadResult`, driven by
   whether every applied coefficient is sourced. Nothing special-cases it here; the mode simply
   cannot produce anything else, and M4-08 puts that value on the API response so no adapter can
   drop it silently.
4. **The provisional set stays out of the core.** It lives in `:server` under
   `dimensioning/adapter/provisional`, not in `commonMain` and not in `:core`'s production source
   set, so the KMP core still has no shippable placeholder in it and the PWA at M7 inherits nothing.

## Consequences

- M4 can be built and verified end to end while the ⚑ gate stays open, which is the point.
- **The risk the worksheet named is real and is now accepted, not eliminated.** Its words were that a
  placeholder formula set "is exactly the artefact that quietly becomes authoritative — wired into a
  demo, the demo becomes a pilot, and a `SOURCE_TBD` coefficient reaches a homeowner." The four
  properties above are mitigations against *accident*. None of them stops a determined operator who
  sets the property and ignores the banner. What protects against that is PAC-42 closing.
- The mitigation this ADR relies on most is the third: the indicative status travels with the result
  rather than with the deployment, so a number that escapes into a PDF or a screen carries its own
  warning. M5 and M7 must render it. If either ships a devis that does not display it, this decision
  becomes the wrong one and should be revisited.
- When PAC-42 closes, a validated formula set is added and `pacpilot.dimensioning.method=validated`
  becomes the only value used outside development. The provisional adapter is then deleted, not left
  configurable — a superseding ADR records that.
- The coefficients remain deliberately non-physical, as M2-07 made them: U-values that rise with
  newer construction, air's volumetric heat capacity at 1.000. A reviewer who sees a heat load
  computed from them cannot mistake it for a study.

## Alternatives considered

**Refuse to start, as the worksheet proposed.** The safest option and the one the worksheet leaned
toward. Rejected here because it makes M4's end-to-end proof impossible while a *domain* gate is
open, coupling engineering progress to a decision that needs a person with EN 12831 in front of them.
The end-to-end flow is exactly what M8's sync work assumes has already been exercised.

**Refuse in production, inject a validated set in tests only.** Considered closely and genuinely
attractive: it honours the worksheet and still lets the integration tests run. Rejected because the
test-only formula set becomes the only way to see the product work at all, so a demo either does not
happen or happens by wiring the test fixture into a running server — which is the accident this ADR
is trying to prevent, arrived at by a less visible route.

**Ship a plausible method now and refine later.** Rejected, and it remains rejected — it is the thing
`CLAUDE.md` §12 exists to forbid. Indicative mode computes with numbers that are *obviously* wrong;
it does not compute with numbers that look right.
