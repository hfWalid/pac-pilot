package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.EffectiveDate

/** The published identity of one barème version. Referenced by every devis it priced. */
data class AidRulePackVersion(val value: String) {
    init {
        require(value.isNotBlank()) { "AidRulePackVersion must not be blank" }
        require(value == value.trim()) { "AidRulePackVersion must not carry surrounding whitespace" }
    }

    override fun toString(): String = value
}

/**
 * A versioned, immutable barème: MaPrimeRénov', CEE and TVA as they stood over one date range
 * (`CLAUDE.md` §4.4, §7).
 *
 * This is the reproducibility anchor of the whole product. Rules are **never** live database
 * queries — a devis written in 2026 must still price against the 2026 barème when it is audited in
 * 2029, so a barème change publishes a *new* pack and never mutates an old one. A pack that could
 * be edited would silently rewrite every past devis that referenced it.
 *
 * This aggregate models the **envelope only**: which pack, when it applied, and the checksum that
 * proves the payload was not tampered with in transit or on the device. The payload's structure and
 * its evaluation belong to the M3 aids engine, and putting a half-guessed rule schema here now would
 * be inventing barème structure ahead of the sources (§12).
 *
 * [effectiveTo] is **inclusive** — a barème published "applicable jusqu'au 30 juin" ends on the
 * 30th, and the successor starts on the 1st. `null` means still in force.
 */
data class AidRulePack(
    val version: AidRulePackVersion,
    val effectiveFrom: EffectiveDate,
    val effectiveTo: EffectiveDate?,
    /** Verified by the client on pull and by the server on resolve (§7). Opaque to the domain. */
    val checksum: String,
) {

    init {
        require(checksum.isNotBlank()) { "a published pack carries a checksum" }
        if (effectiveTo != null) {
            require(effectiveFrom <= effectiveTo) {
                "a pack's range runs forward, was " + effectiveFrom.render() + " to " + effectiveTo.render()
            }
        }
    }

    /**
     * Whether a devis dated [date] prices against this pack.
     *
     * The resolution rule of §7, and the reason [EffectiveDate] exists. Both boundary days belong to
     * the pack; a gap or an overlap between successive packs is a publication error the M6 pipeline
     * is responsible for refusing, not something this type can detect on its own.
     */
    fun covers(date: EffectiveDate): Boolean =
        date >= effectiveFrom && (effectiveTo == null || date <= effectiveTo)
}
