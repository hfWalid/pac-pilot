package fr.pacpilot.core.aids.model

import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.Percentage

/** A pack source or artefact could not be read. Names the origin and, where one applies, the line. */
class AidRulePackFormatException(origin: String, line: Int, problem: String) :
    RuntimeException(if (line > 0) "$origin line $line: $problem" else "$origin: $problem")

/**
 * Reads the encoded barème format — both the human-authored source and the published artefact.
 *
 * **In `commonMain`, and that placement is the point.** Three parties read this format: the pipeline
 * that publishes ([fr.pacpilot.core] via `:rulepacks`), the server that resolves, and the device that
 * pulls and verifies (`CLAUDE.md` §7). Three parsers would eventually disagree about what a file
 * means, and the disagreement would surface as a checksum mismatch that looks like tampering.
 *
 * It was briefly written as pipeline tooling. That was wrong for exactly this reason, and moving it
 * here is what lets the PWA read a published pack at M7 without a second implementation.
 *
 * **Deliberately tiny and dependency-free**, like the golden-vector parser and for the same reason:
 * the file's real audience is a person holding an `anah.gouv.fr` page beside it.
 */
object AidRulePackFormat {

    private const val CHECKSUM = "checksum"
    private const val SIGNATURE = "signature"

    /** The header a published artefact carries above the source it was made from. */
    fun writePublished(sourceText: String, checksum: String, signature: String): String =
        "# Published pack — do not edit. Immutable once published (CLAUDE.md §4.4).\n" +
            "$CHECKSUM = $checksum\n" +
            "$SIGNATURE = $signature\n\n" +
            sourceText

    /**
     * Reads a published artefact: the source body plus the checksum and signature.
     *
     * Re-parses the body rather than trusting a summary, so what is read is what was written.
     */
    fun readPublished(content: String, origin: String): AidRulePack {
        var checksum: String? = null
        var signature: String? = null
        val body = StringBuilder()

        content.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            when {
                line.startsWith("$CHECKSUM ") || line.startsWith("$CHECKSUM=") ->
                    checksum = line.substringAfter('=').trim()
                line.startsWith("$SIGNATURE ") || line.startsWith("$SIGNATURE=") ->
                    signature = line.substringAfter('=').trim()
                else -> body.append(raw).append('\n')
            }
        }

        val resolvedChecksum = checksum
            ?: throw AidRulePackFormatException(origin, 0, "a published pack carries a checksum")
        val resolvedSignature = signature
            ?: throw AidRulePackFormatException(origin, 0, "a published pack carries a signature")

        val source = readSource(body.toString(), origin)
        return AidRulePack(
            version = source.version,
            effectiveFrom = source.effectiveFrom,
            effectiveTo = source.effectiveTo,
            payload = AidRulePackPayload(source.vatRate, source.aids),
            checksum = resolvedChecksum,
            signature = resolvedSignature,
        )
    }

    /** An encoded source, parsed but not validated, checksummed or signed. */
    data class Source(
        val version: AidRulePackVersion,
        val effectiveFrom: EffectiveDate,
        val effectiveTo: EffectiveDate?,
        val vatRate: VatRate,
        val aids: List<AidRule>,
    )

    fun readSource(content: String, origin: String): Source {
        val header = mutableMapOf<String, String>()
        val sections = mutableListOf<Section>()
        var current: Section? = null

        content.lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed

            if (line.startsWith("[") && line.endsWith("]")) {
                current = Section(line.substring(1, line.length - 1).trim(), lineNumber)
                sections.add(current!!)
                return@forEachIndexed
            }

            if (!line.contains('=')) {
                throw AidRulePackFormatException(origin, lineNumber, "expected 'key = value', got '$line'")
            }
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim()

            val section = current
            if (section == null) {
                header[key] = value
            } else if (section.entries.put(key, value) != null) {
                throw AidRulePackFormatException(origin, lineNumber, "duplicate key '$key' in [${section.name}]")
            }
        }

        return assemble(header, sections, origin)
    }

    private fun assemble(header: Map<String, String>, sections: List<Section>, origin: String): Source {
        val version = AidRulePackVersion(header.required("version", origin))
        val from = date(header.required("effective-from", origin), origin)
        val to = header["effective-to"]?.takeIf { it.isNotBlank() }?.let { date(it, origin) }

        var vatRate: VatRate? = null
        val aids = mutableListOf<AidRule>()

        sections.forEach { section ->
            when (section.name) {
                "vat" -> vatRate = VatRate(section.percentage("rate", origin), section.required("source", origin))
                "aid income-tiered" -> aids.add(section.incomeTiered(origin))
                "aid forfait" -> aids.add(section.forfait(origin))
                "aid rate-based" -> aids.add(section.rateBased(origin))
                else -> throw AidRulePackFormatException(
                    origin,
                    section.line,
                    "unknown section '[${section.name}]'; expected [vat], [aid income-tiered], " +
                        "[aid forfait] or [aid rate-based]",
                )
            }
        }

        return Source(
            version = version,
            effectiveFrom = from,
            effectiveTo = to,
            vatRate = vatRate
                ?: throw AidRulePackFormatException(origin, 0, "no [vat] section; every pack states the rate in force"),
            aids = aids,
        )
    }

    private class Section(val name: String, val line: Int) {
        val entries = mutableMapOf<String, String>()
    }

    private fun Section.incomeTiered(origin: String): AidRule.IncomeTiered {
        val byDecile = entries
            .filterKeys { it.startsWith("decile.") }
            .map { (key, value) ->
                IncomeDecile(
                    key.removePrefix("decile.").toIntOrNull()
                        ?: throw AidRulePackFormatException(origin, line, "'$key' is not decile.N"),
                ) to euros(value, key, origin, line)
            }
            .sortedBy { it.first.value }
            .toMap()

        if (byDecile.isEmpty()) {
            throw AidRulePackFormatException(origin, line, "[aid income-tiered] has no decile.N entries; it pays nobody")
        }

        return AidRule.IncomeTiered(
            id = AidRuleId(required("id", origin)),
            label = required("label", origin),
            source = required("source", origin),
            amountByDecile = byDecile,
            cap = entries["cap"]?.takeIf { it.isNotBlank() }?.let { euros(it, "cap", origin, line) },
        )
    }

    private fun Section.forfait(origin: String): AidRule.Forfait = AidRule.Forfait(
        id = AidRuleId(required("id", origin)),
        label = required("label", origin),
        source = required("source", origin),
        amount = euros(required("amount", origin), "amount", origin, line),
    )

    private fun Section.rateBased(origin: String): AidRule.RateBased = AidRule.RateBased(
        id = AidRuleId(required("id", origin)),
        label = required("label", origin),
        source = required("source", origin),
        rate = percentage("rate", origin),
        cap = entries["cap"]?.takeIf { it.isNotBlank() }?.let { euros(it, "cap", origin, line) },
    )

    private fun Section.required(key: String, origin: String): String =
        entries[key]?.takeIf { it.isNotBlank() }
            ?: throw AidRulePackFormatException(origin, line, "[$name] is missing '$key'")

    private fun Map<String, String>.required(key: String, origin: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw AidRulePackFormatException(origin, 0, "missing '$key'")

    private fun Section.percentage(key: String, origin: String): Percentage =
        Percentage(twoDecimalsToMinorUnits(required(key, origin), key, origin, line).toInt())

    private fun euros(value: String, key: String, origin: String, line: Int): MoneyEur =
        MoneyEur(twoDecimalsToMinorUnits(value, key, origin, line))

    /**
     * `1234.56` to `123456`, exactly, without floating point anywhere.
     *
     * Hand-rolled rather than `BigDecimal`, which is JVM-only and would not compile to JS — the same
     * reason `EffectiveDate` does its own calendar arithmetic.
     */
    private fun twoDecimalsToMinorUnits(value: String, key: String, origin: String, line: Int): Long {
        val negative = value.startsWith("-")
        val unsigned = if (negative) value.substring(1) else value
        val whole = unsigned.substringBefore('.')
        val fraction = if (unsigned.contains('.')) unsigned.substringAfter('.') else ""

        if (whole.isEmpty() || !whole.all { it.isDigit() } || !fraction.all { it.isDigit() } || fraction.length > 2) {
            throw AidRulePackFormatException(
                origin,
                line,
                "'$key = $value' is not a number with at most two decimals",
            )
        }

        val minor = whole.toLong() * 100 + (fraction.padEnd(2, '0').toLongOrNull() ?: 0L)
        return if (negative) -minor else minor
    }

    private fun date(value: String, origin: String): EffectiveDate {
        val parts = value.split("-")
        if (parts.size != 3) {
            throw AidRulePackFormatException(origin, 0, "'$value' is not a date in YYYY-MM-DD form")
        }
        return EffectiveDate(
            parts[0].toIntOrNull() ?: throw AidRulePackFormatException(origin, 0, "'$value' has a non-numeric year"),
            parts[1].toIntOrNull() ?: throw AidRulePackFormatException(origin, 0, "'$value' has a non-numeric month"),
            parts[2].toIntOrNull() ?: throw AidRulePackFormatException(origin, 0, "'$value' has a non-numeric day"),
        )
    }
}
