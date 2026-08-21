package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC

/**
 * Everything the dimensioning depended on, frozen at the moment it was computed.
 *
 * This is the reproducibility anchor for a study (`CLAUDE.md` §9, §10): a devis is defensible years
 * later only if the *inputs* were persisted, not just the number that came out. A study recomputed
 * from this snapshot and the same formula set must produce the same heat load, forever.
 *
 * **Why both [climateZone] and [baseTemperature].** The zone is the human-meaningful classification
 * an installer recognises; the base temperature is the value the formula actually consumes, and it
 * is resolved from zone *and* département (§6a). Recording only the zone would make a study
 * irreproducible the day a département's tabulated base temperature is corrected. Recording the
 * resolved value is the resolved-context pattern: external context is resolved at the boundary and
 * passed inward as immutable data, so the engine never reaches out to look anything up.
 */
data class InputsSnapshot(
    val surface: SurfaceM2,
    val ceilingHeight: CeilingHeightM,
    val constructionPeriod: ConstructionPeriod,
    val insulationLevel: InsulationLevel,
    val ventilationType: VentilationType,
    val emitterType: EmitterType,
    val climateZone: ClimateZone,
    /** Resolved outdoor design temperature for this site — the negative one the load is computed at. */
    val baseTemperature: TemperatureC,
    /** The indoor temperature the installation is sized to hold. */
    val targetIndoorTemperature: TemperatureC,
) {
    init {
        require(baseTemperature < targetIndoorTemperature) {
            "the outdoor design temperature must be below the indoor target, was " +
                baseTemperature.render() + " against " + targetIndoorTemperature.render()
        }
    }
}
