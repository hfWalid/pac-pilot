package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.DimensioningId
import fr.pacpilot.core.shared.InstallerId
import fr.pacpilot.core.shared.InstantUtc
import fr.pacpilot.core.shared.SiteId

/**
 * One heat-loss study for one site: what went in, what came out, and whether a professional has
 * signed it.
 *
 * Core-owned aggregate (DELIVERY-PLAN §3). It lives in `commonMain` because both targets need it —
 * the PWA computes it offline, the server recomputes it from [inputs] and asserts equality
 * (`CLAUDE.md` §4.2). Persistence is a `:server` adapter's problem; there is nothing about storage
 * in this file and there must not be.
 *
 * Immutable. [validate] returns a new instance rather than mutating, so a study that has been
 * signed cannot be altered by a stale reference held somewhere else.
 */
data class Dimensioning(
    val id: DimensioningId,
    val siteId: SiteId,
    val inputs: InputsSnapshot,
    val result: HeatLoadResult,
    /** `null` until a professional signs. Never inferred — absence means unsigned. */
    val validation: ValidationAct?,
) {

    val isValidated: Boolean get() = validation != null

    /**
     * Records the artisan taking responsibility for this study.
     *
     * **Re-validation is refused.** A study that has already been signed and may already sit on a
     * printed devis must not silently acquire a second signature or a later timestamp; that would
     * rewrite the evidential record the audit chain depends on (PRODUCT-VIEWS #8). Re-deciding
     * means a new study, which is a new [DimensioningId] and a new row — an addition to the record,
     * not an edit of it.
     */
    fun validate(installer: InstallerId, at: InstantUtc): Dimensioning {
        require(validation == null) {
            "dimensioning $id is already validated; compute a new study rather than re-signing this one"
        }
        return copy(validation = ValidationAct(installer, at))
    }

    companion object {
        /** A freshly computed, unsigned study — the only state a result is born in (§4.5). */
        fun computed(
            id: DimensioningId,
            siteId: SiteId,
            inputs: InputsSnapshot,
            result: HeatLoadResult,
        ): Dimensioning = Dimensioning(id, siteId, inputs, result, validation = null)
    }
}
