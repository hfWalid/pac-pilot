package fr.pacpilot.core

import fr.pacpilot.core.aids.engine.AidsEngine
import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.HeatPumpType
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.ReplacedSystem
import fr.pacpilot.core.aids.port.RulePackRepository
import fr.pacpilot.core.dimensioning.engine.DimensioningEngine
import fr.pacpilot.core.dimensioning.engine.ProvisionalFormulaSetProvider
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome
import fr.pacpilot.core.dimensioning.model.EmitterType
import fr.pacpilot.core.dimensioning.model.InputsSnapshot
import fr.pacpilot.core.dimensioning.model.InsulationLevel
import fr.pacpilot.core.dimensioning.model.VentilationType
import fr.pacpilot.core.dimensioning.port.FormulaSetProvider
import fr.pacpilot.core.shared.CeilingHeightM
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.ElectricalSupplyKva
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.SurfaceM2
import fr.pacpilot.core.shared.TemperatureC

/**
 * JS-only façade over `commonMain`.
 *
 * **Glue only, and that constraint is load-bearing.** Anything with behaviour belongs in
 * `commonMain`, or the one-source-two-targets property (ARCHITECTURE #3) quietly erodes — the whole
 * bet is that the installer's tablet and the server compute from the same code, and a calculation
 * that lives only here would be the first crack in it.
 *
 * Everything crossing this boundary is a primitive or a string. Kotlin value classes, sealed
 * hierarchies and `Map`s are awkward or impossible to consume from TypeScript, so the conversion
 * happens here rather than in the PWA — which is also what lets the screens stay ignorant of Kotlin.
 *
 * Amounts and measurements cross as their **exact minor units** (cents, watts, deci-degrees), never
 * as floating-point decimals, for the same reason the domain holds them that way: a devis computed
 * on a tablet and re-verified on the server must land on the same cent.
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
object CoreFacade {

    fun identify(): String = CoreInfo.identify()

    /**
     * Runs the heat-loss study.
     *
     * The formula set is supplied by the caller through [installFormulaSet]; there is no default,
     * for the same reason ADR-0015 gives the server no default — a method nobody chose is a method
     * nobody is responsible for.
     */
    fun runDimensioning(
        surfaceCentiM2: Int,
        ceilingHeightCm: Int,
        constructionPeriod: String,
        insulationLevel: String,
        ventilationType: String,
        emitterType: String,
        climateZone: String,
        baseTemperatureDeciC: Int,
        targetIndoorTemperatureDeciC: Int,
        electricalSupplyKva: Int,
        effectiveDate: String,
    ): DimensioningResultJs {
        val engine = DimensioningEngine(
            formulaSets ?: error("no formula set installed; call installFormulaSet first"),
        )

        val snapshot = InputsSnapshot(
            surface = SurfaceM2(surfaceCentiM2),
            ceilingHeight = CeilingHeightM(ceilingHeightCm),
            constructionPeriod = ConstructionPeriod.valueOf(constructionPeriod),
            insulationLevel = InsulationLevel.valueOf(insulationLevel),
            ventilationType = VentilationType.valueOf(ventilationType),
            emitterType = EmitterType.valueOf(emitterType),
            climateZone = ClimateZone.valueOf(climateZone),
            baseTemperature = TemperatureC(baseTemperatureDeciC),
            targetIndoorTemperature = TemperatureC(targetIndoorTemperatureDeciC),
            availableElectricalPower = ElectricalSupplyKva(electricalSupplyKva),
        )

        return when (val outcome = engine.run(snapshot, date(effectiveDate))) {
            is DimensioningOutcome.Computed -> DimensioningResultJs(
                outcome = "COMPUTED",
                heatLoadKw = outcome.result.heatLoad.render(),
                heatLoadWatts = outcome.result.heatLoad.watts,
                powerBandMinimumKw = outcome.result.recommendedPowerBand.minimum.render(),
                powerBandMinimumWatts = outcome.result.recommendedPowerBand.minimum.watts,
                powerBandMaximumKw = outcome.result.recommendedPowerBand.maximum.render(),
                powerBandMaximumWatts = outcome.result.recommendedPowerBand.maximum.watts,
                flowTemperatureC = outcome.result.recommendedFlowTemperature?.render(),
                confidence = outcome.result.confidence.name,
                provisional = outcome.result.isProvisional,
                assumptions = outcome.result.assumptions.entries
                    .map { AssumptionJs(it.statement, it.source, it.isProvisional) }
                    .toTypedArray(),
                refusalReasons = emptyArray(),
                refusalStatements = emptyArray(),
            )

            is DimensioningOutcome.ManualStudyRequired -> DimensioningResultJs(
                outcome = "MANUAL_STUDY_REQUIRED",
                heatLoadKw = null,
                heatLoadWatts = 0,
                powerBandMinimumKw = null,
                powerBandMinimumWatts = 0,
                powerBandMaximumKw = null,
                powerBandMaximumWatts = 0,
                flowTemperatureC = null,
                confidence = null,
                provisional = false,
                assumptions = emptyArray(),
                refusalReasons = outcome.reasons.map { it.name }.toTypedArray(),
                // The domain's own wording, in the language the installer works in. The screen may
                // present it; it must not invent a different meaning.
                refusalStatements = outcome.reasons.map { it.statement }.toTypedArray(),
            )
        }
    }

    /**
     * Resolves the aids for a job against whatever packs are cached on the device.
     *
     * Returns a refusal rather than zero when no pack covers the date — zero is a claim about the
     * household, a refusal is a statement about the system (ADR-0017).
     */
    fun resolveAids(
        incomeDecile: Int,
        heatPumpType: String,
        climateZone: String,
        replacedSystem: String,
        workCostCents: Double,
        effectiveDate: String,
    ): AidsResultJs {
        val engine = AidsEngine(rulePacks ?: EmptyRulePacks)

        val inputs = AidsInputs(
            incomeDecile = IncomeDecile(incomeDecile),
            heatPumpType = HeatPumpType.valueOf(heatPumpType),
            climateZone = ClimateZone.valueOf(climateZone),
            replacedSystem = ReplacedSystem.valueOf(replacedSystem),
            workCost = MoneyEur(workCostCents.toLong()),
        )

        return when (val outcome = engine.resolve(inputs, date(effectiveDate))) {
            is AidsOutcome.Resolved -> {
                val resolution = outcome.resolution
                AidsResultJs(
                    outcome = "RESOLVED",
                    packVersion = resolution.aids.packVersion.value,
                    lines = resolution.aids.lines
                        .map { AidLineJs(it.rule.value, it.label, it.amount.render(), it.amount.cents.toDouble(), it.source) }
                        .toTypedArray(),
                    totalAids = resolution.aids.total.render(),
                    vat = resolution.vat.render(),
                    estimatedTotalIncludingVat = resolution.estimatedTotalIncludingVat.render(),
                    estimatedResteACharge = resolution.estimatedResteACharge.amount.render(),
                    overGranted = resolution.estimatedResteACharge.isOverGranted,
                    refusalDate = null,
                )
            }

            is AidsOutcome.NoPackPublished -> AidsResultJs(
                outcome = "NO_PACK_PUBLISHED",
                packVersion = null,
                lines = emptyArray(),
                totalAids = null,
                vat = null,
                estimatedTotalIncludingVat = null,
                estimatedResteACharge = null,
                overGranted = false,
                refusalDate = outcome.effectiveDate.render(),
            )
        }
    }

    /** Renders an amount in exact cents the way the domain does — one rounding rule everywhere. */
    fun renderMoney(cents: Double): String = MoneyEur(cents.toLong()).render()

    private var formulaSets: FormulaSetProvider? = null
    private var rulePacks: RulePackRepository? = null

    /** Installed by the PWA at start-up. Not a default: see [runDimensioning]. */
    fun installFormulaSet(provider: FormulaSetProvider) {
        formulaSets = provider
    }

    /**
     * Installs the provisional method — placeholder coefficients, every result `INDICATIVE`.
     *
     * **Named so it cannot be called by accident** (ADR-0021). There is no default and no fallback:
     * a PWA that does not call this computes nothing, which is the same posture the server takes
     * with `pacpilot.dimensioning.method`. Until the ⚑ gate (PAC-42) closes there is nothing else to
     * install, and the screens show a degraded-mode banner whenever this one is in force.
     */
    fun installProvisionalFormulaSet() {
        formulaSets = ProvisionalFormulaSetProvider()
    }

    /** True while the installed method is unvalidated — what the degraded-mode banner reads. */
    fun isMethodProvisional(): Boolean = formulaSets is ProvisionalFormulaSetProvider

    fun installRulePacks(repository: RulePackRepository) {
        rulePacks = repository
    }

    private fun date(iso: String): EffectiveDate {
        val parts = iso.split("-")
        require(parts.size == 3) { "effectiveDate must be YYYY-MM-DD, was '$iso'" }
        return EffectiveDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }

    /** No packs cached yet — the honest state until one is pulled, and until PAC-75 publishes one. */
    private object EmptyRulePacks : RulePackRepository {
        override fun packEffectiveOn(effectiveDate: EffectiveDate) = null
    }
}

@JsExport
@OptIn(ExperimentalJsExport::class)
class AssumptionJs(val statement: String, val source: String, val provisional: Boolean)

@JsExport
@OptIn(ExperimentalJsExport::class)
class AidLineJs(
    val rule: String,
    val label: String,
    val amount: String,
    val amountCents: Double,
    val source: String,
)

@JsExport
@OptIn(ExperimentalJsExport::class)
class DimensioningResultJs(
    val outcome: String,
    val heatLoadKw: String?,
    val heatLoadWatts: Int,
    val powerBandMinimumKw: String?,
    val powerBandMinimumWatts: Int,
    val powerBandMaximumKw: String?,
    val powerBandMaximumWatts: Int,
    val flowTemperatureC: String?,
    val confidence: String?,
    val provisional: Boolean,
    val assumptions: Array<AssumptionJs>,
    val refusalReasons: Array<String>,
    val refusalStatements: Array<String>,
)

@JsExport
@OptIn(ExperimentalJsExport::class)
class AidsResultJs(
    val outcome: String,
    val packVersion: String?,
    val lines: Array<AidLineJs>,
    val totalAids: String?,
    val vat: String?,
    val estimatedTotalIncludingVat: String?,
    val estimatedResteACharge: String?,
    val overGranted: Boolean,
    val refusalDate: String?,
)
