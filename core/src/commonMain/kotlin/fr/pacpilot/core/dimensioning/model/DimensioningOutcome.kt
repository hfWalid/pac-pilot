package fr.pacpilot.core.dimensioning.model

/**
 * Why the method declined to answer.
 *
 * A named type rather than a bare `String` so the refusal reads as domain vocabulary, and so the
 * enumerated envelope conditions can replace the free text later without changing every signature.
 *
 * Deliberately **not** an enum today. The set of conditions that put a dwelling outside the
 * validated envelope *is* the simplified method, and the method is exactly what the M2 ⚑ gate
 * settles (`CLAUDE.md` §6a). Enumerating members now would be inventing the envelope.
 */
data class RefusalReason(val statement: String) {
    init {
        require(statement.isNotBlank()) { "a refusal must say why the method declined" }
    }

    override fun toString(): String = statement
}

/**
 * What running the dimensioning produced — a result, or a refusal to produce one.
 *
 * **The refusal is the point.** Inputs outside the validated envelope must yield "étude manuelle
 * requise", never a fabricated number (`PRODUCT-VIEWS` #9, `CLAUDE.md` §12). A nullable result or a
 * zero heat load would let a caller read a plausible-looking figure out of a case the method
 * explicitly could not handle, and that figure would end up on a devis.
 *
 * Sealed, so a caller has to handle both branches, and [ManualStudyRequired] simply has no heat
 * load to read: the mistake is not caught at review, it does not compile.
 *
 * Only [Computed] becomes a [Dimensioning] aggregate. A refusal is an answer, not a study — there
 * is nothing for an installer to validate and nothing to persist as evidence.
 */
sealed interface DimensioningOutcome {

    /** The method answered. [result] carries the load, the band, the guidance and the reasoning. */
    data class Computed(val result: HeatLoadResult) : DimensioningOutcome

    /** The method declined. Non-empty: a refusal that cannot say why is not actionable. */
    data class ManualStudyRequired(val reasons: List<RefusalReason>) : DimensioningOutcome {
        init {
            require(reasons.isNotEmpty()) { "a refusal states at least one reason" }
        }
    }
}
