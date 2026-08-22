package fr.pacpilot.core.dimensioning.engine

import fr.pacpilot.core.dimensioning.model.Assumption
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.ValidatedEnvelope
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.dimensioning.port.FlowTemperatureGuidance
import fr.pacpilot.core.dimensioning.port.FormulaSet
import fr.pacpilot.core.dimensioning.port.Sourced
import fr.pacpilot.core.shared.AirChangeRate
import fr.pacpilot.core.shared.EnvelopeAreaFactor
import fr.pacpilot.core.shared.Percentage
import fr.pacpilot.core.shared.ThermalTransmittance
import fr.pacpilot.core.shared.VolumetricHeatCapacity

/**
 * A formula set that writes the assumptions log as a side effect of being read.
 *
 * **The log is derived, never duplicated.** If the engine could obtain a coefficient without an
 * entry appearing, the log would be a second source of truth — correct the day it was written and
 * wrong the first time somebody added a coefficient and forgot to mirror it. Here the read *is* the
 * record, so the two cannot disagree.
 *
 * Order is call order, which is application order, which is the order a human follows the reasoning
 * in (PRODUCT-VIEWS #5, #8). The log is read by a domain reviewer at the ⚑ gate and by a QualiPAC
 * auditor years later — the statements are written for them, in the language they work in, not as
 * debug output.
 */
internal class RecordingFormulaSet(private val delegate: FormulaSet) : FormulaSet {

    private val recorded = mutableListOf<Assumption>()

    val assumptions: List<Assumption> get() = recorded.toList()

    override val envelope: ValidatedEnvelope get() = delegate.envelope

    override fun uValueFor(
        period: ConstructionPeriod,
        insulation: InsulationLevel,
    ): Sourced<ThermalTransmittance> =
        delegate.uValueFor(period, insulation).also {
            record(
                it,
                "Coefficient U par defaut pour une construction ${period.name} " +
                    "avec isolation ${insulation.name} : ${it.value.render()} W/(m2.K)",
            )
        }

    override fun airChangeRateFor(ventilation: VentilationType): Sourced<AirChangeRate> =
        delegate.airChangeRateFor(ventilation).also {
            record(
                it,
                "Taux de renouvellement d'air pour ventilation ${ventilation.name} : " +
                    "${it.value.render()} vol/h",
            )
        }

    override fun airVolumetricHeatCapacity(): Sourced<VolumetricHeatCapacity> =
        delegate.airVolumetricHeatCapacity().also {
            record(it, "Capacite thermique volumique de l'air : ${it.value.render()} Wh/(m3.K)")
        }

    override fun envelopeAreaFactor(): Sourced<EnvelopeAreaFactor> =
        delegate.envelopeAreaFactor().also {
            record(
                it,
                "Surface deperditive estimee a ${it.value.render()} m2 par m2 de surface habitable " +
                    "(simplification : pas de releve des parois)",
            )
        }

    override fun underSizingMargin(): Sourced<Percentage> =
        delegate.underSizingMargin().also {
            record(it, "Marge de sous-dimensionnement acceptee : ${it.value.render()} %")
        }

    override fun overSizingMargin(): Sourced<Percentage> =
        delegate.overSizingMargin().also {
            record(it, "Marge de sur-dimensionnement acceptee : ${it.value.render()} %")
        }

    override fun flowTemperatureFor(emitter: EmitterType): FlowTemperatureGuidance =
        delegate.flowTemperatureFor(emitter).also { guidance ->
            recorded += when (guidance) {
                is FlowTemperatureGuidance.Advised -> Assumption(
                    "Loi d'eau pour emetteurs ${emitter.name} : temperature de depart " +
                        "${guidance.flowTemperature.render()} C",
                    guidance.source,
                )
                // A withheld guidance is a finding, not silence. The installer must be able to see
                // that the method declined rather than that it forgot.
                is FlowTemperatureGuidance.Withheld -> Assumption(
                    "Loi d'eau pour emetteurs ${emitter.name} : aucune preconisation validee",
                    guidance.source,
                )
            }
        }

    private fun record(sourced: Sourced<*>, statement: String) {
        recorded += sourced.asAssumption(statement)
    }
}
