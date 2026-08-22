package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.EffectiveDate

/**
 * What resolving the aids produced — a priced resolution, or a refusal to price.
 *
 * The aids-side twin of `DimensioningOutcome`, and it exists for the same reason. `CLAUDE.md` §4.4
 * forbids pricing a devis against anything but the barème in force on its own date, so a date no
 * published pack covers has exactly one honest answer: none. The tempting alternatives are both
 * wrong in ways nobody would notice — falling back to the nearest pack produces a plausible figure
 * from a barème that did not apply, and returning empty aids produces a reste-à-charge equal to the
 * full price, which reads as "you qualify for nothing" rather than "we cannot say".
 *
 * Sealed, so a caller has to handle both branches, and [NoPackPublished] simply has no figures to
 * read: the mistake does not compile rather than being caught at review.
 *
 * This is the shape M1-09 could not yet commit to. The port then returned an [AidsResolution]
 * directly, which left the `null` from `RulePackRepository` with nowhere to go but an exception or
 * a silent substitution — M3-02's ticket asked for the decision, and this is it.
 */
sealed interface AidsOutcome {

    /** The barème answered. [resolution] carries the lines, the VAT and the reste-à-charge. */
    data class Resolved(val resolution: AidsResolution) : AidsOutcome

    /**
     * No published pack covers [effectiveDate], so nothing can be priced.
     *
     * Carries the date rather than a message: the adapter presenting this decides the wording, and
     * the M6 pipeline needs the date to tell a publication gap from a devis predating the scheme.
     */
    data class NoPackPublished(val effectiveDate: EffectiveDate) : AidsOutcome
}
