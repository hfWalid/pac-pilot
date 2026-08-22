package fr.pacpilot.core.dimensioning.model

import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC

/**
 * The range of dwellings the method has actually been validated for.
 *
 * **Data, not predicates, and supplied with the formula set rather than hardcoded.** The envelope
 * *is* part of the method: a revision that widens it must be a new formula set, reviewable at the ⚑
 * gate and quotable in an ADR, not a code change. A lambda-based envelope could be none of those.
 *
 * The product's defensibility rests on refusing rather than on answering widely (PRODUCT-VIEWS #11,
 * where "simplified method rejected by auditors/insurers" is a top-right existential risk). A
 * narrow envelope with frequent refusals is the safer outcome, not the worse one.
 */
data class ValidatedEnvelope(
    val minimumSurface: SurfaceM2,
    val maximumSurface: SurfaceM2,
    val minimumCeilingHeight: CeilingHeightM,
    val maximumCeilingHeight: CeilingHeightM,
    val minimumBaseTemperature: TemperatureC,
    val maximumBaseTemperature: TemperatureC,
    val coveredConstructionPeriods: Set<ConstructionPeriod>,
    val coveredInsulationLevels: Set<InsulationLevel>,
    val coveredVentilationTypes: Set<VentilationType>,
    val coveredEmitterTypes: Set<EmitterType>,
) {

    init {
        require(minimumSurface <= maximumSurface) { "the surface range runs upward" }
        require(minimumCeilingHeight <= maximumCeilingHeight) { "the ceiling-height range runs upward" }
        require(minimumBaseTemperature <= maximumBaseTemperature) {
            "the base-temperature range runs upward"
        }
        require(coveredConstructionPeriods.isNotEmpty()) { "a method covers at least one period" }
        require(coveredInsulationLevels.isNotEmpty()) { "a method covers at least one insulation level" }
        require(coveredVentilationTypes.isNotEmpty()) { "a method covers at least one ventilation type" }
        require(coveredEmitterTypes.isNotEmpty()) { "a method covers at least one emitter type" }
    }

    /**
     * Every way these inputs fall outside the envelope — all of them, not the first.
     *
     * An installer standing in a house wants to learn everything wrong in one pass rather than
     * discovering the next problem after fixing the last. An empty list means the method may answer.
     */
    fun violationsFor(inputs: InputsSnapshot): List<RefusalReason> = buildList {
        if (inputs.surface < minimumSurface || inputs.surface > maximumSurface) {
            add(RefusalReason.SURFACE_OUTSIDE_RANGE)
        }
        if (inputs.ceilingHeight < minimumCeilingHeight || inputs.ceilingHeight > maximumCeilingHeight) {
            add(RefusalReason.CEILING_HEIGHT_OUTSIDE_RANGE)
        }
        if (inputs.baseTemperature < minimumBaseTemperature ||
            inputs.baseTemperature > maximumBaseTemperature
        ) {
            add(RefusalReason.BASE_TEMPERATURE_OUTSIDE_RANGE)
        }
        if (inputs.constructionPeriod !in coveredConstructionPeriods) {
            add(RefusalReason.CONSTRUCTION_PERIOD_NOT_COVERED)
        }
        if (inputs.insulationLevel !in coveredInsulationLevels) {
            add(RefusalReason.INSULATION_LEVEL_NOT_COVERED)
        }
        if (inputs.ventilationType !in coveredVentilationTypes) {
            add(RefusalReason.VENTILATION_TYPE_NOT_COVERED)
        }
        if (inputs.emitterType !in coveredEmitterTypes) {
            add(RefusalReason.EMITTER_TYPE_NOT_COVERED)
        }
    }
}
