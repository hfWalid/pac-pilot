package fr.pacpilot.core.aids.port

import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsResolution
import fr.pacpilot.core.shared.EffectiveDate

/**
 * Driving port — compute the aids and the reste-à-charge for one job (`ARCHITECTURE` #5).
 *
 * [effectiveDate] is the devis date, and it is the whole reproducibility mechanism: it selects the
 * rule pack, and the pack version travels back inside the result so the same devis reprices
 * identically in three years (`CLAUDE.md` §7).
 *
 * Implemented at M3.
 */
interface ResolveAids {

    fun resolve(inputs: AidsInputs, effectiveDate: EffectiveDate): AidsResolution
}
