# Golden vectors

Immutable input→output fixtures that bind the JVM and JS targets of the core together
(CLAUDE.md §4.2, ARCHITECTURE #3). Both targets run the same fixtures through the same suite, so a
formula that behaves differently on the installer's phone than on the server fails in CI — instead
of surfacing as a divergence flag in front of a homeowner.

## The rules

**Append-only.** A published vector is never edited and never deleted. It is the frozen record of
what the engines were agreed to produce. If a result must change, then the barème or the method
changed: add a new vector with a new id and leave the old one describing the old rule version. This
is what makes a three-year-old devis reproducible.

**Every vector carries a `source`.** Either a citation (`anah.gouv.fr`, `fiche BAR-TH-171`,
`EN 12831 §6.3`) or the literal `SOURCE_TBD` with a reason. A vector asserting an unsourced number
that looks authoritative is worse than no vector at all (CLAUDE.md §12). Enforced by the suite.

**Ids are unique and stable.** They are the handle used in review and in failure messages.

**Deterministic.** No clocks, no randomness, no locale dependence. The effective date is an input.
Verified by running the suite under a hostile timezone and locale.

## Adding a vector

Edit or add a `.vectors` file in this directory. No test wiring changes.

```
[vector]
id          = dimensioning-h1-radiators-001
description = 120 m², 1975, H1, radiators — heat load at base temperature
source      = SOURCE_TBD (awaiting the M2 method validation gate)
operation   = dimensioning.heatLoad
input.surfaceM2          = 120
input.constructionPeriod = BEFORE_1975
expect.heatLoadKw = 12.4
```

Blank-line-separated blocks, `key = value`, `#` starts a comment. `input.*` and `expect.*` are
repeatable; at least one `expect.*` is required.

Then add the `operation` to the dispatcher in `GoldenVectorSuite.evaluate`. Dispatch is explicit
rather than reflective on purpose: an unknown operation fails loudly instead of being silently
skipped, which would let a vector look green while testing nothing.

## Why these are embedded rather than read as resources

`:core:jsTest` runs in a browser under Karma — no filesystem — and KMP has no common
resource-loading API. The `generateGoldenVectors` Gradle task embeds these files into a generated
`commonTest` source file, which is what lets one fixture set run byte-identically on both targets.
Authoring stays in plain data files so a domain reviewer can check a vector against an official
barème, and so adding one is a readable diff rather than escaped Kotlin.
