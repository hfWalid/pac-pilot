package fr.pacpilot.core.aids.engine

import fr.pacpilot.core.aids.model.AidsInputs
import fr.pacpilot.core.aids.model.AidsOutcome
import fr.pacpilot.core.aids.model.AidsResolution
import fr.pacpilot.core.aids.model.HeatPumpType
import fr.pacpilot.core.aids.model.IncomeDecile
import fr.pacpilot.core.aids.model.ReplacedSystem
import fr.pacpilot.core.aids.model.ResteACharge
import fr.pacpilot.core.quoting.model.LineItem
import fr.pacpilot.core.shared.ClimateZone
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one arithmetic mistake in this product that would never be spotted by eye: TVA subtracted
 * as though it were an aid. The resulting reste-à-charge is wrong by twice the VAT and still looks
 * entirely reasonable on a devis.
 */
class AidsVatSemanticsTest {

    private val engine = AidsEngine(InMemoryRulePackRepository.withSamplePacks())
    private val duringFirstHalf = EffectiveDate(2025, 3, 1)

    private fun resolution(workCost: MoneyEur): AidsResolution {
        val outcome = engine.resolve(
            AidsInputs(
                incomeDecile = IncomeDecile(3),
                heatPumpType = HeatPumpType.AIR_WATER,
                climateZone = ClimateZone.H1,
                replacedSystem = ReplacedSystem.OIL_BOILER,
                workCost = workCost,
            ),
            duringFirstHalf,
        )
        assertTrue(outcome is AidsOutcome.Resolved)
        return outcome.resolution
    }

    @Test
    fun `TVA raises what is owed and is never an aid line`() {
        // Sample pack H1 publishes 10 %. 10 000 HT -> 1 000 TVA -> 11 000 TTC.
        val resolved = resolution(MoneyEur.ofEuros(10_000))

        assertEquals("1000.00", resolved.vat.render())
        assertEquals("11000.00", resolved.estimatedTotalIncludingVat.render())
        assertTrue(
            resolved.aids.lines.none { line -> line.label.contains("TVA", ignoreCase = true) },
            "TVA appeared as an aid line",
        )
    }

    @Test
    fun `treating TVA as an aid would understate the reste a charge by twice the VAT`() {
        // The decisive test, written down so a future refactor cannot reintroduce the bug quietly.
        // Correct: aids come off the TTC total.
        // Wrong:   TVA is added to the aid pile and comes off the HT cost instead.
        val resolved = resolution(MoneyEur.ofEuros(10_000))

        val correct = resolved.estimatedResteACharge.amount
        val ifVatWereAnAid = ResteACharge.of(
            resolved.workCost,
            resolved.aids.copy(lines = resolved.aids.lines + fakeVatAsAid(resolved.vat)),
        ).amount

        assertEquals(
            resolved.vat + resolved.vat,
            correct - ifVatWereAnAid,
            "the error is the VAT counted twice: once not added, once subtracted",
        )
    }

    /**
     * TVA dressed up as an aid line — only ever constructed here, to measure the damage.
     *
     * Note that [fr.pacpilot.core.aids.model.AidLine] would refuse this outright if the amount were
     * negative, which is one of the reasons M1-07 gave it that invariant.
     */
    private fun fakeVatAsAid(vat: MoneyEur) = fr.pacpilot.core.aids.model.AidLine(
        rule = fr.pacpilot.core.aids.model.AidRuleId("tva-mismodelled"),
        label = "TVA 10 % (mismodelled as an aid)",
        amount = vat,
        source = "SOURCE_TBD (never published; exists only to prove the bug)",
    )

    @Test
    fun `the engine's TTC total agrees with a devis whose lines carry the same rate`() {
        // Quote.totalIncludingVat is the fold of LineItem.totalIncludingVat (M1-08). For lines that
        // sum to the work cost and carry the pack's rate, the two paths must land on the same cent.
        val resolved = resolution(MoneyEur.ofEuros(12_000))
        val rate = resolved.appliedVatRate.rate

        val lines = listOf(
            LineItem("PAC air-eau", MoneyEur.ofEuros(9_000), 1, rate),
            LineItem("Pose", MoneyEur.ofEuros(3_000), 1, rate),
        )
        val devisTtc = lines.fold(MoneyEur.ZERO) { running, line -> running + line.totalIncludingVat }

        assertEquals(devisTtc, resolved.estimatedTotalIncludingVat)
    }

    @Test
    fun `per-line VAT and whole-cost VAT can differ by a cent, and the devis is authoritative`() {
        // A real, reachable divergence rather than a hypothetical. Three lines of 33,33 EUR at 10 %
        // round to 3,33 each (9,99 total); the same 99,99 HT taxed once rounds to 10,00.
        //
        // The two are not redundant computations of one number — TVA applies conditionally per line
        // (CLAUDE.md 6b, M1-08), so the devis is the authoritative figure and this resolution's TTC
        // is what the aids path can compute when no devis exists yet. M4 wires the quote-building
        // path, and that is where the resolution must take the devis total rather than recompute
        // it; recorded on PAC-51 rather than papered over here.
        val awkward = MoneyEur(9_999)
        val resolved = resolution(awkward)
        val rate = resolved.appliedVatRate.rate

        val lines = List(3) { LineItem("Lot", MoneyEur(3_333), 1, rate) }
        val devisTtc = lines.fold(MoneyEur.ZERO) { running, line -> running + line.totalIncludingVat }

        assertEquals("10.00", resolved.vat.render(), "whole-cost VAT")
        assertEquals("9.99", lines.fold(MoneyEur.ZERO) { r, l -> r + l.vat }.render(), "per-line VAT")
        assertEquals(MoneyEur(1), resolved.estimatedTotalIncludingVat - devisTtc, "the gap is exactly one cent")
    }

    @Test
    fun `a provisional VAT rate makes the whole pack provisional`() {
        assertTrue(SampleAidRulePacks.FIRST_HALF.payload.vatRate.isProvisional)
        assertTrue(SampleAidRulePacks.FIRST_HALF.isProvisional)
    }
}
