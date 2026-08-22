# M2 ⚑ gate worksheet — the simplified dimensioning method

- **Status:** open. Blocked on human validation (PAC-42).
- **Drafted:** 2026-08-22
- **Becomes:** `docs/decisions/0011-simplified-dimensioning-method.md`, status `accepted`, once filled.

> **Why this is not an ADR yet.** `docs/decisions/README.md` is explicit that an ADR is one file per
> *locked* decision, and that open questions are deliberately not recorded there because writing them
> up would falsely imply a decision had been made. Nothing below is decided except sections 2 and 6.
> A numbered ADR sitting in that directory half-empty would be exactly the false implication the
> convention exists to prevent.
>
> This worksheet is the ADR's draft. Fill it, then move it to `docs/decisions/0011-…`, set the status
> to `accepted`, and add the index row. That is step 6 of *How to close* at the bottom.
>
> **Until then:** the engine runs on `ProvisionalFormulaSet`, every result reports `INDICATIVE`
> confidence, and nothing here is authoritative.
>
> **Sections 2 and 6 are the only ones already decided.** They are engineering decisions and were
> made without domain input. Everything else needs a person with EN 12831 open.

- **Relates to:** [ADR-0002](decisions/0002-kotlin-multiplatform-core.md),
  [ADR-0009](decisions/0009-golden-vectors-are-append-only.md)

---

## Context

The exact set of simplifications that is both fast enough for a 15-minute on-site pre-visit **and**
defensible in front of a QualiPAC auditor or a decennial insurer is not settled domain knowledge
(`CLAUDE.md` §6a). PRODUCT-VIEWS #11 places "simplified method rejected by auditors/insurers" in the
top-right quadrant — one of two existential risks, and a domain risk rather than a technical one.

M2 was therefore built so this decision could be made **late** and applied **cheaply**. As of merge
`f416a5a`:

- `DimensioningEngine` computes, and contains no coefficient literal — `grep` for decimal literals
  in `dimensioning/engine/` and `dimensioning/port/` returns nothing.
- `FormulaSet` declares an accessor per coefficient, each returning `Sourced<T>` — a value and its
  provenance, inseparably.
- `ValidatedEnvelope` is data carried by the formula set, so the method's limits are versioned with
  the method.
- `RecordingFormulaSet` writes the assumptions log as a side effect of reading a coefficient, so the
  log cannot drift from what was actually applied.
- 29 golden vectors bind the JVM and JS targets; 12 exercise the engine.

**Closing this ADR costs writing values, not code.** No file in `dimensioning/` needs to change for
a validated method to take effect, unless the gate rejects the *structural form* in section 1.

Reference material: **EN 12831** (paid, AFNOR) and **3CL-DPE** (public).

---

## 1. Structural form of the method — PROPOSED, needs ratification

The engine currently computes:

```
heat load = transmission losses + air-renewal losses,   both across ΔT

  ΔT                = target indoor temperature − resolved base temperature
  transmission      = U × (floor area × envelope-area factor) × ΔT
  air renewal       = volumetric heat capacity of air × air-change rate × (floor area × ceiling height) × ΔT
```

Two simplifications are embedded here, and both are the operator's to accept or reject:

**(a) A single global U-value rather than per-wall fabric.** The installer reads a construction
period and an insulation level off the building; no wall-by-wall survey happens. This is what makes
a 15-minute visit possible.

**(b) Envelope area inferred from floor area by one factor.** *This is the assumption an auditor
questions first.* It collapses building geometry — detached versus terraced, one storey versus two,
compact versus sprawling — into a single multiplier. A dwelling with unusual geometry will be
mis-sized, and the mitigation is the envelope in section 3 rather than a better factor.

**Ratify, or replace.** If the validated method needs a different shape — a per-surface breakdown, a
geometry input, an intermittency or thermal-bridge term — say so here and `DimensioningEngine`
changes with it. That is a normal M2 outcome, not a failure.

> ⚠️ If the method requires `Math.pow`, `sqrt` or any transcendental function, **record it
> explicitly**. JS engines are permitted implementation-defined precision for those, so the JVM and
> browser targets could disagree — which is the one thing the golden vectors exist to prevent.
> Basic `+ − × ÷` are bit-identical on both and are safe.

---

## 2. Where the provisional formula set lives — DECIDED

`ProvisionalFormulaSet` lives in `commonTest`. It is not on the production classpath of either
target, so shipping it is impossible by construction rather than discouraged by a guard.

**Consequence, and the decision it forces:** `:server` at M4 and the PWA at M7 will have nothing to
boot against until a validated formula set exists. The intended behaviour is that a build without a
validated method **refuses to start** rather than computing with placeholders. Confirm or overrule
that here — it is the last cheap moment.

---

## 3. The validated envelope — TO BE FILLED

Where the method holds, and therefore where the engine must return `ManualStudyRequired`.

**A narrower envelope is the safer outcome.** Defensibility rests on refusing, not on answering
widely. Every row left wide is a row an auditor may test.

| Dimension | Minimum | Maximum | Source | Why it bounds |
|---|---|---|---|---|
| Heated surface | | | | Below/above this the single-U simplification stops holding |
| Ceiling height | | | | Feeds heated volume directly |
| Base temperature | | | | Outside the tabulated département range there is no validated input |

| Categorical | Covered members | Source |
|---|---|---|
| `ConstructionPeriod` | | |
| `InsulationLevel` | | |
| `VentilationType` | | |
| `EmitterType` | | |

---

## 4. Coefficients — TO BE FILLED

Every row is an accessor on `FormulaSet`. Each needs a value **and** a citation; `SOURCE_TBD` is
permitted only where the gate consciously leaves something provisional, with a reason.

### 4.1 Default U-values — `uValueFor(period, insulation)`

Unit: W/(m²·K). Fifteen combinations. Drives the transmission term, so an error here scales the
whole result.

| Construction period | NONE | PARTIAL | GOOD | Source |
|---|---|---|---|---|
| `BEFORE_1975` | | | | |
| `FROM_1975_TO_1989` | | | | |
| `FROM_1990_TO_2000` | | | | |
| `FROM_2001_TO_2012` | | | | |
| `AFTER_2012` | | | | |

### 4.2 Air-change rates — `airChangeRateFor(ventilation)`

Unit: volumes per hour.

| Ventilation | Rate | Source |
|---|---|---|
| `NATURAL` | | |
| `VMC_SIMPLE_FLUX` | | |
| `VMC_DOUBLE_FLUX` | | |

> Open question already recorded at M1-04: does the hygro-A / hygro-B distinction within
> simple-flux change the rate enough to need modelling? If yes, `VentilationType` grows a member.

### 4.3 Single-value coefficients

| Accessor | Unit | Value | Source | Notes |
|---|---|---|---|---|
| `airVolumetricHeatCapacity` | Wh/(m³·K) | | | Physical, but still a number — still cited |
| `envelopeAreaFactor` | m²/m² | | | The section 1(b) simplification, in one number |
| `underSizingMargin` | % | | | Failure mode: a cold client in February |
| `overSizingMargin` | % | | | Failure mode: short-cycling and early failure |

The two margins are **independent** by design, because their failure modes are not symmetric. If
the validated method treats them as one symmetric tolerance, say so and simplify the accessor.

### 4.4 Loi d'eau guidance — `flowTemperatureFor(emitter)`

Each emitter is either **advised** with a flow temperature, or explicitly **withheld** with a
reason. Withheld is a legitimate answer and does not block a study; it is recorded in the
assumptions log so the installer sees the method declined rather than forgot.

| Emitter | Advised (°C) or WITHHELD | Source |
|---|---|---|
| `RADIATOR_HIGH_TEMPERATURE` | | |
| `RADIATOR_LOW_TEMPERATURE` | | |
| `UNDERFLOOR_HEATING` | | |
| `FAN_COIL` | | |

---

## 5. The M1-04 enumerations — RATIFY OR CORRECT

These were marked `TODO(unverified)` when the model was written and are open here. **Changing one
after M4 persists snapshots is a schema migration**, so this is the cheap moment.

- **`ConstructionPeriod`** — `BEFORE_1975` came from the repository's own worked example in the
  golden-vector README; the other four boundaries follow the RT milestones. Do they match the
  buckets 3CL-DPE actually uses?
- **`InsulationLevel`** — the weakest of the three. An installer who cannot reliably tell `PARTIAL`
  from `GOOD` standing in a cellar makes the distinction worthless. This is a field-interview
  question, not a desk one.
- **`EmitterType`** — do these four cover the installed base an artisan actually meets?
- **`VentilationType`** — see 4.2.

---

## 6. Determinism and reproducibility — DECIDED, unchanged by this ADR

- No clock and no randomness in the core; both enforced by ArchUnit, not convention.
- The effective date is a parameter on `FormulaSetProvider`, so a study recomputed years later
  applies the method as it stood when the devis was written.
- Rounding happens in exactly one place in the engine, written with integer comparisons rather than
  a platform rounding call, because tie-breaking is not guaranteed identical across targets.
- Golden vectors are append-only ([ADR-0009](decisions/0009-golden-vectors-are-append-only.md)). When this
  ADR is accepted, real vectors are **added**; the provisional ones stay, describing what the
  provisional method produced.

---

## Consequences

**Once accepted.** Results stop reporting `INDICATIVE` and start reporting `SUPPORTED` wherever
every applied coefficient is cited. Nothing else in the product changes shape — the assumptions log,
the confidence indication and the refusal path all already work off what the formula set supplies.

**If the structural form in section 1 is rejected.** `DimensioningEngine` changes, the provisional
golden vectors stay as a record of the old shape, and new ones are added. The ports and the model do
not change.

**What this ADR does not cover.** Aids barèmes (M3/M6, own gate), catalogue data, and the base
temperature per département — that last one is reference data seeded through versioned migrations at
M4, resolved at the boundary into `InputsSnapshot`, deliberately not a `FormulaSet` accessor so
there is only one path to the number.

---

## Alternatives considered

**Ship a plausible method now, refine later.** Rejected, and it is the reason this gate exists. An
unsourced number that looks authoritative is worse than no number (`CLAUDE.md` §12): it reaches a
devis, then an insurer's file, and there is no point at which anyone is prompted to check it.

**Let an agent derive coefficients from public sources.** Rejected. Not because the research is
worthless — 3CL-DPE is public and worth mining — but because the gate's function is that a
*qualified professional* takes responsibility for the method, which is the same principle as
`ValidationAct` one layer up. An agent can gather candidates; it cannot sign.

**Widen the envelope to reduce refusals.** Rejected. Refusing is the mitigation for the top-right
risk in PRODUCT-VIEWS #11, not a limitation to be minimised.

---

## How to close this gate

1. Fill sections 3, 4 and 5. Every value gets a citation, or an explicit `SOURCE_TBD` with a reason.
2. Ratify or replace section 1; confirm or overrule section 2.
3. Write a validated `FormulaSet` implementation. Placement is a real decision — see section 2.
4. Add real golden vectors alongside the provisional ones. Never edit or delete a published vector.
5. Resolve or re-affirm the `TODO(unverified)` markers in `DwellingCharacteristics.kt`.
6. Move this file to `docs/decisions/0011-simplified-dimensioning-method.md`, set its
   status to `accepted`, date it, and add the row to the ADR index.
7. Verification is a review, not a build: run the method by hand against this ADR for three real
   dwellings of different periods and zones, and confirm the engine reproduces the same figures.
   Then take one dwelling deliberately outside the envelope and confirm it refuses.

**The question to answer honestly before moving this into `decisions/`:** would this method, and this ADR,
survive being handed to a QualiPAC auditor or a decennial insurer? If the answer is "probably", the
envelope is too wide — narrow it and refuse more.
