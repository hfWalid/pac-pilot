package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.InstallerId
import fr.pacpilot.core.shared.InstantUtc

/**
 * The artisan taking responsibility for a computed result.
 *
 * This type *is* the liability framing of `CLAUDE.md` §4.5. The product is an aide à la décision:
 * every result is a proposal until a qualified professional signs it, and the record of who signed
 * and when has to survive independently of the computation that produced it. Merging the two — a
 * `validated: Boolean` on the result — would destroy exactly the distinction an insurer or a
 * QualiPAC auditor asks about.
 *
 * The instant is supplied, never read: the core has no clock (§10).
 */
data class ValidationAct(val validatedBy: InstallerId, val validatedAt: InstantUtc)
