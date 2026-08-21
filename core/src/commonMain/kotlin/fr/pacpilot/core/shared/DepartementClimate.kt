package fr.pacpilot.core.shared

/**
 * A French département, by its INSEE code.
 *
 * A string because the codes are not numbers: `2A` and `2B` for Corsica, and the overseas codes
 * have three digits. Parsing them as integers is the classic bug.
 */
data class Departement(val code: String) {

    init {
        require(code.isNotBlank()) { "a departement has a code" }
        require(code == code.trim()) { "a departement code carries no surrounding whitespace" }
        require(code == code.uppercase()) { "departement codes are upper case, was '$code'" }
    }

    override fun toString(): String = code
}

/**
 * The climate reference data for one département: its zone and its outdoor design temperature.
 *
 * **The shape only. This file contains no values, deliberately.** `CLAUDE.md` §6a names the base
 * temperature by département as a dimensioning input, and §12 forbids shipping an unsourced number
 * that looks authoritative. The table itself is reference data seeded through versioned migrations
 * at M4 and validated behind the M2 ⚑ gate — never a constant hardcoded in the domain, where it
 * would look settled and could not be corrected without a release.
 *
 * Modelling the association here is what lets the resolution happen at the boundary: an adapter
 * looks the département up, and [InputsSnapshot][fr.pacpilot.core.dimensioning.model.InputsSnapshot]
 * records the resolved [baseTemperature] so the study stays reproducible even if the table is later
 * corrected.
 */
data class DepartementClimate(
    val departement: Departement,
    val zone: ClimateZone,
    val baseTemperature: TemperatureC,
    /** A citation, or `SOURCE_TBD` while the value is unvalidated. Never blank. */
    val source: String,
) {
    init {
        require(source.isNotBlank()) {
            "a base temperature cites its source or says plainly it has none ($SOURCE_TBD)"
        }
    }

    val isProvisional: Boolean get() = source.startsWith(SOURCE_TBD)

    companion object {
        const val SOURCE_TBD: String = "SOURCE_TBD"
    }
}
