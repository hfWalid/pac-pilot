package fr.pacpilot.core.shared

/**
 * The opaque references the core needs to name things it does not own.
 *
 * Kept in one file because they are one idea with several types, not several ideas. Distinct types
 * rather than a generic `Identifier<T>` or a bare `String`: a `QuoteId` must never be assignable to
 * a `DimensioningId`, and a generic wrapper reads badly from Java (`:server` is Java — ADR-0010).
 *
 * **The core never generates these.** Ids are client-generated UUIDs created offline (CLAUDE.md
 * §4.3, §8), and generation is randomness — which the engines are forbidden (determinism, §10).
 * They arrive as inputs. Validating only that the value is present and untrimmed keeps the UUID
 * *shape* where it belongs, at the adapter that mints it, while still refusing the empty string
 * that would otherwise flow silently into a persisted aggregate.
 */
private fun requireIdentifier(value: String, type: String) {
    require(value.isNotBlank()) { "$type must not be blank" }
    require(value == value.trim()) { "$type must not carry surrounding whitespace, was '$value'" }
}

/** A dimensioning study. Core-owned aggregate (DELIVERY-PLAN §3). */
data class DimensioningId(val value: String) {
    init { requireIdentifier(value, "DimensioningId") }
    override fun toString(): String = value
}

/** A devis. Core-owned aggregate (DELIVERY-PLAN §3). */
data class QuoteId(val value: String) {
    init { requireIdentifier(value, "QuoteId") }
    override fun toString(): String = value
}

/** A site, owned by the Dossier context server-side. The core only ever refers to one. */
data class SiteId(val value: String) {
    init { requireIdentifier(value, "SiteId") }
    override fun toString(): String = value
}

/** A catalogue entry, owned by the Catalog context server-side and read-mostly. */
data class ProductId(val value: String) {
    init { requireIdentifier(value, "ProductId") }
    override fun toString(): String = value
}

/** The artisan. Owned by Identity server-side; the core needs it only to record who validated. */
data class InstallerId(val value: String) {
    init { requireIdentifier(value, "InstallerId") }
    override fun toString(): String = value
}
