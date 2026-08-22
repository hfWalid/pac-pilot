package fr.pacpilot.core.dimensioning.model

/**
 * One thing the method assumed rather than measured, and where that assumption comes from.
 *
 * The confidence note in `CLAUDE.md` §6a, made into data. An installer defending a sizing to an
 * auditor needs to point at what was inferred and on whose authority — "the U-value came from the
 * construction period, per 3CL-DPE" is a defence; a bare kilowatt figure is not.
 */
data class Assumption(val statement: String, val source: String) {

    init {
        require(statement.isNotBlank()) { "an assumption must say what was assumed" }
        require(source.isNotBlank()) {
            "an assumption must cite a source or say plainly that it has none ($SOURCE_TBD)"
        }
    }

    val isProvisional: Boolean get() = source.startsWith(SOURCE_TBD)

    companion object {
        /**
         * Mirrors `GoldenVector.SOURCE_TBD` in `commonTest` deliberately rather than sharing it:
         * the domain cannot depend on the test source set, and an unsourced number must be
         * self-declaring in production data, not only in fixtures (`CLAUDE.md` §12).
         */
        const val SOURCE_TBD: String = "SOURCE_TBD"
    }
}

/**
 * The assumptions behind one result, in the order the method made them.
 *
 * [isProvisional] is the property that matters: a study resting on any unsourced coefficient is not
 * something to print on a devis as authoritative. M2 wires this to the injected `FormulaSet`, where
 * every coefficient carries its citation or `SOURCE_TBD`; until that gate closes, every real study
 * will report `true` here, which is the honest answer.
 */
data class AssumptionsLog(val entries: List<Assumption>) {

    val isProvisional: Boolean get() = entries.any { it.isProvisional }
}
