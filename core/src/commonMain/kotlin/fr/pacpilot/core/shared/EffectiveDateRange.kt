package fr.pacpilot.core.shared

/**
 * A span of days a versioned artefact is in force for.
 *
 * Extracted so the containment rule lives in one place. Both the aid rule packs and, from M2, the
 * formula sets are resolved by asking which version covered the devis date, and two independent
 * implementations of that question is how a devis ends up priced by one pack and recomputed against
 * another (`CLAUDE.md` §4.4, §7).
 *
 * **[to] is inclusive.** A barème published *"applicable jusqu'au 30 juin"* ends on the 30th, and
 * its successor starts on the 1st. An exclusive end would leave the handover day covered by nothing
 * at all, and a devis written on that day irreproducible. `null` means still in force.
 *
 * A gap or an overlap between successive ranges is a publication error the M6 pipeline must refuse.
 * A single range cannot detect one.
 */
data class EffectiveDateRange(val from: EffectiveDate, val to: EffectiveDate?) {

    init {
        if (to != null) {
            require(from <= to) {
                "a range runs forward, was " + from.render() + " to " + to.render()
            }
        }
    }

    /** Both boundary days belong to the range. */
    fun contains(date: EffectiveDate): Boolean = date >= from && (to == null || date <= to)

    /** `true` while no end date has been published. */
    val isOpenEnded: Boolean get() = to == null

    override fun toString(): String = from.render() + ".." + (to?.render() ?: "")
}
