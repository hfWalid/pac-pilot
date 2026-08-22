package fr.pacpilot.core.dimensioning.model

/**
 * Why the method declined to answer.
 *
 * A closed set, keyed one-to-one to the dimensions of [ValidatedEnvelope]. It was free text at
 * M1-05 because the conditions that put a dwelling outside the envelope *are* the simplified
 * method, and the method was not yet settled — closing the set is M2-02's job now that the envelope
 * is modelled.
 *
 * Closed rather than open so the compiler enumerates the refusals a UI has to present, and so a new
 * envelope dimension cannot be added without someone deciding what an installer should be told.
 *
 * [statement] is the domain's own wording, in the language the installer works in. Adapters may
 * present it directly or re-render it; what they must not do is invent a different meaning.
 */
enum class RefusalReason(val statement: String) {
    SURFACE_OUTSIDE_RANGE("Surface habitable hors du domaine valide de la methode"),
    CEILING_HEIGHT_OUTSIDE_RANGE("Hauteur sous plafond hors du domaine valide de la methode"),
    BASE_TEMPERATURE_OUTSIDE_RANGE("Temperature de base hors du domaine valide de la methode"),
    CONSTRUCTION_PERIOD_NOT_COVERED("Periode de construction non couverte par la methode"),
    INSULATION_LEVEL_NOT_COVERED("Niveau d'isolation non couvert par la methode"),
    VENTILATION_TYPE_NOT_COVERED("Type de ventilation non couvert par la methode"),
    EMITTER_TYPE_NOT_COVERED("Type d'emetteur non couvert par la methode"),
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
