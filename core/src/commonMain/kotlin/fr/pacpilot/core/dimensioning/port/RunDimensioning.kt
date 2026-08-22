package fr.pacpilot.core.dimensioning.port

import fr.pacpilot.core.dimensioning.model.DimensioningOutcome
import fr.pacpilot.core.dimensioning.model.InputsSnapshot
import fr.pacpilot.core.shared.EffectiveDate

/**
 * Driving port — run a heat-loss study (`ARCHITECTURE` #5).
 *
 * One method for one use case from the pre-visit flow, coarse on purpose: a port per method would
 * make the SDK ambition of `CLAUDE.md` §3 a matter of exposing internals rather than handing a
 * partner a use case.
 *
 * The implementation arrives at M2, behind the ⚑ method-validation gate. What this interface fixes
 * now is the *shape*: an [EffectiveDate] must be supplied, so no engine can reach for a clock to
 * decide which formula set applies (§10); and the return type is [DimensioningOutcome], so refusing
 * to answer is part of the contract rather than an exception or a null.
 */
interface RunDimensioning {

    fun run(inputs: InputsSnapshot, effectiveDate: EffectiveDate): DimensioningOutcome
}
