# Encoded barème sources

**One file per published pack.** Human-authored, version-controlled, and reviewed like code — because
reviewing one against its official source *is* the ⚑ gate (PAC-75).

## Why this format and not JSON

The same reasoning M0-07 applied to the golden vectors: plain data files over escaped code, so a
domain reviewer can hold the file beside an `anah.gouv.fr` page and check them line by line, and so
a barème change produces a legible diff. A format only a machine can read makes the gate impossible
to perform honestly.

## The format

```
version        = 2026-H1
effective-from = 2026-01-01
effective-to   = 2026-06-30          # inclusive; omit while still in force

[vat]
rate   = 5.50                        # percent, two decimals
source = <citation>

[aid income-tiered]                  # MaPrimeRénov'-shaped: amount by income decile
id       = <stable id>
label    = <what the homeowner sees>
source   = <citation>
cap      = <euros>                   # optional
decile.1 = <euros>
decile.2 = <euros>
...                                  # only the deciles the scheme actually pays

[aid forfait]                        # CEE-shaped: a fixed amount per fiche
id     = <stable id>
label  = <label>
source = <citation>
amount = <euros>

[aid rate-based]                     # a proportion of the work cost
id     = <stable id>
label  = <label>
source = <citation>
rate   = <percent>
cap    = <euros>                     # optional
```

`#` starts a comment. Blank lines are ignored.

## Rules

**Every rule carries a citation, and it must be re-checkable a year later.** `anah.gouv.fr` alone is
not: name the page or the fiche reference *and* the date it was consulted. `AidRule` refuses a blank
source outright, so an unsourced rule cannot even be constructed — the format makes that obvious
rather than letting it fail late.

**`effective-to` is inclusive** (M1-07). A barème *"applicable jusqu'au 30 juin"* ends on the 30th and
its successor starts on the 1st. This is the most likely encoding error, and the pipeline's gap and
overlap checks catch it against the published series.

**Three mechanisms, and only three.** `AidRulePackPayload` models exactly `IncomeTiered`, `Forfait`
and `RateBased`. This format expresses those and nothing else — a fourth mechanism is a deliberate,
reviewable change to the model, not a data-entry convenience.

## What is not here

**No real barème values yet.** Encoding them is PAC-75, the ⚑ human gate: an agent can encode what it
is told and cross-check arithmetic, but it cannot decide that a plafond is right. Until that gate
closes this directory holds only the format's own fixtures.
