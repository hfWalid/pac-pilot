package fr.pacpilot.core.aids.model

/**
 * The canonical rendering of a pack's content — what a checksum is taken over.
 *
 * **In `commonMain` because both sides verify.** `CLAUDE.md` §7 has the client verify the checksum on
 * pull and the server verify it on resolve. Two implementations of "what exactly is hashed" would
 * eventually disagree, and the failure would look like tampering rather than like a bug.
 *
 * **Only the rendering lives here, not the hash.** Kotlin's common standard library has no SHA-256,
 * and adding a multiplatform crypto dependency to the domain for one hash would put a library where
 * ADR-0008 keeps the catalogue honest. Each target hashes this string with its own primitive —
 * `MessageDigest` on the JVM, `crypto.subtle` in the browser — which is the same split M1 made for
 * signatures: the domain describes, the platform computes.
 *
 * Everything that could vary is fixed: rules sorted by id, deciles sorted numerically, amounts as
 * exact minor units rather than formatted decimals. It deliberately does **not** reuse `render()` —
 * those are presentation and may change; this must not.
 */
object AidRulePackCanonicalForm {

    /**
     * [version] and the date range are included because a pack whose payload matches its
     * predecessor's but whose range differs is a different pack, and must not share a checksum.
     */
    fun of(
        version: AidRulePackVersion,
        effectiveFrom: fr.pacpilot.core.shared.EffectiveDate,
        effectiveTo: fr.pacpilot.core.shared.EffectiveDate?,
        payload: AidRulePackPayload,
    ): String = buildString {
        append("version=").append(version.value).append('\n')
        append("from=").append(effectiveFrom.render()).append('\n')
        append("to=").append(effectiveTo?.render() ?: "").append('\n')
        append("vat=").append(payload.vatRate.rate.basisPoints).append('|').append(payload.vatRate.source).append('\n')

        payload.aids.sortedBy { it.id.value }.forEach { rule ->
            append(render(rule)).append('\n')
        }
    }

    /** The canonical form of an already-assembled pack. */
    fun of(pack: AidRulePack): String =
        of(pack.version, pack.effectiveFrom, pack.effectiveTo, pack.payload)

    private fun render(rule: AidRule): String {
        val head = rule.id.value + "|" + rule.label + "|" + rule.source + "|"
        return when (rule) {
            is AidRule.IncomeTiered ->
                head + "income-tiered|" +
                    rule.amountByDecile.entries
                        .sortedBy { it.key.value }
                        .joinToString(",") { it.key.value.toString() + ":" + it.value.cents } +
                    "|cap=" + (rule.cap?.cents?.toString() ?: "")
            is AidRule.Forfait -> head + "forfait|" + rule.amount.cents
            is AidRule.RateBased ->
                head + "rate-based|" + rule.rate.basisPoints + "|cap=" + (rule.cap?.cents?.toString() ?: "")
        }
    }
}
