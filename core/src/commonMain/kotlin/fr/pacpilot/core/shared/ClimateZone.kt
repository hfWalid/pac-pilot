package fr.pacpilot.core.shared

/**
 * The French climatic zone a site sits in.
 *
 * Deliberately carries **no coefficients**. A base temperature per zone is a tabulated domain
 * number, and this project's standing rule is that an unsourced number which looks authoritative is
 * worse than no number at all (CLAUDE.md §12). Those values arrive with the M2 method-validation
 * gate ⚑, as an injectable `FormulaSet` with each coefficient carrying its citation or
 * `SOURCE_TBD` — not as constants attached here where they would look settled.
 *
 * Only the three top-level zones are modelled. The finer subdivisions (H1a…H3) and the altitude
 * correction change *which coefficient applies*, not which zone a site is in, so they belong to the
 * formula set too. `TODO(unverified)`: confirm at M2 whether the simplified method needs the
 * subdivision as a separate input.
 */
enum class ClimateZone {
    H1,
    H2,
    H3,
}
