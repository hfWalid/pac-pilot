package fr.pacpilot.core.aids.port

import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.shared.EffectiveDate

/**
 * Driving port — compute the aids and the reste-à-charge for one job (`ARCHITECTURE` #5).
 *
 * [effectiveDate] is the devis date, and it is the whole reproducibility mechanism: it selects the
 * rule pack, and the pack version travels back inside the result so the same devis reprices
 * identically in three years (`CLAUDE.md` §7).
 *
 * Returns an [AidsOutcome] rather than a resolution, because a date no published pack covers must
 * be refused explicitly (§4.4). M1-09 declared this returning the resolution directly, before there
 * was anywhere for that refusal to go; M3-03 widened it rather than leaving the engine to throw.
 *
 * Implemented at M3 by `AidsEngine`.
 */
interface ResolveAids {

    fun resolve(inputs: AidsInputs, effectiveDate: EffectiveDate): AidsOutcome
}
