package fr.pacpilot.core.aids.port

import fr.pacpilot.core.aids.model.AidRulePack
import fr.pacpilot.core.shared.EffectiveDate

/**
 * Driven port — find the barème in force on a date.
 *
 * The one thing the aids engine needs from the outside world. Satisfiable from a browser with
 * IndexedDB behind it and from a server with Postgres or an object store behind it, with no change
 * here: nothing in the signature names a transport, a store or a cache.
 *
 * Returns `null` when no published pack covers the date. That is a real condition rather than an
 * error — a devis dated before the first pack, or inside a publication gap, must be refused
 * explicitly and not priced against the nearest pack (`CLAUDE.md` §4.4).
 *
 * Implemented at M3, published to by the M6 pipeline.
 */
interface RulePackRepository {

    fun packEffectiveOn(effectiveDate: EffectiveDate): AidRulePack?
}
