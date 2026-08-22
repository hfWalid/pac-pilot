package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.DimensioningId
import fr.pacpilot.core.shared.InstallerId
import fr.pacpilot.core.shared.InstantUtc
import fr.pacpilot.core.shared.SiteId

/**
 * One heat-loss study for one site: what went in, what came out, and whether a professional has
 * signed it.
 *
 * Core-owned aggregate (DELIVERY-PLAN §3), in `commonMain` because both targets need it — the PWA
 * computes it offline, the server recomputes it from [inputs] and asserts equality (`CLAUDE.md`
 * §4.2). Persistence is a `:server` adapter's problem; there is nothing about storage here.
 *
 * **Sealed rather than a nullable field.** Signed and unsigned are different things with different
 * consequences and different audiences (§4.5), so they are different types. A caller cannot read a
 * signature that is not there, and cannot forget to check — the compiler asks. An
 * `isValidated: Boolean` over a nullable field would put that check back on everyone's memory,
 * which is the shortcut the whole liability model cannot afford.
 *
 * **Neither case is a `data class`, deliberately.** `copy()` on a validated study would let
 * `copy(inputs = …)` produce a new result still carrying the original signature — the artisan's
 * name attached to a calculation they never saw. Identity equality instead: an aggregate is the
 * same aggregate when it has the same id, and value comparison for the sync verification at M8
 * happens on [HeatLoadResult], which is a data class.
 */
sealed interface Dimensioning {

    /** Client-generated, created offline (`CLAUDE.md` §4.3). Never minted by the core. */
    val id: DimensioningId
    val siteId: SiteId
    val inputs: InputsSnapshot
    val result: HeatLoadResult

    companion object {
        /**
         * A freshly computed, unsigned study — the only state a result is born in (§4.5).
         *
         * Takes [DimensioningOutcome.Computed] rather than a bare [HeatLoadResult] so that
         * **a refusal cannot become an aggregate at all**: `ManualStudyRequired` is not accepted
         * here, and the mistake is a compile error rather than a runtime check somebody has to
         * remember to write. There is nothing in a refusal for an installer to validate.
         */
        fun computed(
            id: DimensioningId,
            siteId: SiteId,
            inputs: InputsSnapshot,
            outcome: DimensioningOutcome.Computed,
        ): ComputedDimensioning = ComputedDimensioning(id, siteId, inputs, outcome.result)
    }
}

/** A study that has been calculated but not yet signed. Carries no validation to read. */
class ComputedDimensioning internal constructor(
    override val id: DimensioningId,
    override val siteId: SiteId,
    override val inputs: InputsSnapshot,
    override val result: HeatLoadResult,
) : Dimensioning {

    /**
     * The artisan takes responsibility for this study, and gets back a different type.
     *
     * One-way: there is no path from [ValidatedDimensioning] back here, and no `validate` on it, so
     * re-signing is not refused at runtime — it is unsayable. Re-deciding means computing a new
     * study under a new [DimensioningId], which is an addition to the evidential record rather than
     * an edit of it (PRODUCT-VIEWS #8).
     *
     * The instant is supplied. The core has no clock (§10).
     */
    fun validate(by: InstallerId, at: InstantUtc): ValidatedDimensioning =
        ValidatedDimensioning(id, siteId, inputs, result, ValidationAct(by, at))

    override fun equals(other: Any?): Boolean = other is ComputedDimensioning && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ComputedDimensioning(" + id.value + ")"
}

/**
 * A study a qualified professional has signed.
 *
 * [validation] is non-null by construction: a validated study always knows who and when. Nothing
 * here can be replaced — no `copy`, no setters — so the signature and the calculation it covers
 * cannot drift apart.
 */
class ValidatedDimensioning internal constructor(
    override val id: DimensioningId,
    override val siteId: SiteId,
    override val inputs: InputsSnapshot,
    override val result: HeatLoadResult,
    val validation: ValidationAct,
) : Dimensioning {

    override fun equals(other: Any?): Boolean = other is ValidatedDimensioning && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "ValidatedDimensioning(" + id.value + ", by " + validation.validatedBy.value + ")"
}
