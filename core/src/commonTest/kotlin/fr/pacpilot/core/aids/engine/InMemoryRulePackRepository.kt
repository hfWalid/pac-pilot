package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidRulePack
import fr.pacpilot.core.aids.port.RulePackRepository
import fr.pacpilot.core.shared.EffectiveDate

/**
 * The first implementation of [RulePackRepository], over an in-memory list. Core tests only — the
 * real stores are M6's: IndexedDB on the device, object storage on the server.
 *
 * Deliberately free of caching, I/O and expiry. Those are adapter concerns, and the point of the
 * port is that the same containment question is answered identically from a browser and from a
 * server. Anything clever here would be logic the real adapters would have to reproduce.
 *
 * **Order-independent by construction.** The packs are consulted as a set, not a sequence: no
 * `first`, no `last`, no sort. A repository that resolved by position would pass every test while a
 * caller reordered its fixtures, and "the newest pack quietly wins" is precisely the regression
 * M3-06 exists to catch.
 */
class InMemoryRulePackRepository(private val packs: List<AidRulePack>) : RulePackRepository {

    /**
     * The pack in force on [effectiveDate], or `null` when none is.
     *
     * `null` is a real condition rather than an error (M1-09): a devis dated before the first
     * published pack, or inside a publication gap, must be refused explicitly and never priced
     * against the nearest pack.
     *
     * Containment is [AidRulePack.covers], which delegates to `EffectiveDateRange` — the one place
     * the inclusive-end rule is implemented. A second comparison here is how a devis ends up priced
     * by one pack and recomputed against another.
     *
     * Two packs matching one date **fails loudly**. It is a publication error the M6 pipeline is
     * responsible for refusing, and choosing between them silently would hide a bad publication
     * until an auditor found it — by which point every devis priced in the overlap is suspect.
     */
    override fun packEffectiveOn(effectiveDate: EffectiveDate): AidRulePack? {
        val matching = packs.filter { it.covers(effectiveDate) }
        require(matching.size <= 1) {
            "packs overlap on " + effectiveDate.render() + ": " +
                matching.joinToString(", ") { it.version.value } +
                " — a publication error; the pipeline must refuse to publish an overlap"
        }
        return matching.singleOrNull()
    }

    companion object {
        /** The two adjoining sample versions, which is the minimum fixture for a handover test. */
        fun withSamplePacks(): InMemoryRulePackRepository =
            InMemoryRulePackRepository(SampleAidRulePacks.BOTH)
    }
}
