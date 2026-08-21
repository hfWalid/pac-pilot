package fr.pacpilot.core.quoting.model

import fr.pacpilot.core.aids.model.AidLine
import fr.pacpilot.core.aids.model.AidRulePackVersion
import fr.pacpilot.core.aids.model.ResolvedAids
import fr.pacpilot.core.shared.DimensioningId
import fr.pacpilot.core.shared.EffectiveDate
import fr.pacpilot.core.shared.MoneyEur
import fr.pacpilot.core.shared.ProductId
import fr.pacpilot.core.shared.QuoteId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuoteTest {

    private val packVersion = AidRulePackVersion("2026-H1")

    private fun quote(
        lines: List<LineItem> = listOf(
            LineItem("PAC air-eau 11 kW", MoneyEur.ofEuros(9_500), 1),
            LineItem("Pose et mise en service", MoneyEur.ofEuros(2_500), 1),
            LineItem("Radiateur basse temperature", MoneyEur.ofEuros(500), 4),
        ),
        aids: ResolvedAids = ResolvedAids(
            packVersion,
            listOf(AidLine("MaPrimeRenov", MoneyEur.ofEuros(4_000), "anah.gouv.fr")),
        ),
    ) = Quote(
        id = QuoteId("quote-1"),
        dimensioningId = DimensioningId("dim-1"),
        selectedProduct = ProductId("product-1"),
        lines = lines,
        resolvedAids = aids,
        effectiveDate = EffectiveDate(2026, 8, 21),
        status = QuoteStatus.DRAFT,
    )

    @Test
    fun `sums its lines including quantities`() {
        // 9 500 + 2 500 + (500 x 4) = 14 000
        assertEquals("14000.00", quote().subtotal.render())
    }

    @Test
    fun `a line item totals unit price times quantity`() {
        assertEquals("2000.00", LineItem("Radiateur", MoneyEur.ofEuros(500), 4).total.render())
    }

    @Test
    fun `reste a charge is the subtotal less the resolved aids`() {
        assertEquals("10000.00", quote().resteACharge.amount.render())
        assertTrue(!quote().resteACharge.isOverGranted)
    }

    @Test
    fun `carries the pack version that priced it, so the devis reproduces later`() {
        assertEquals(packVersion, quote().resolvedAids.packVersion)
        assertEquals("2026-08-21", quote().effectiveDate.render())
    }

    @Test
    fun `refuses a devis with no lines`() {
        assertFailsWith<IllegalArgumentException> { quote(lines = emptyList()) }
    }

    @Test
    fun `refuses a line item with a non-positive quantity`() {
        assertFailsWith<IllegalArgumentException> { LineItem("Pose", MoneyEur.ofEuros(100), 0) }
        assertFailsWith<IllegalArgumentException> { LineItem("Pose", MoneyEur.ofEuros(100), -1) }
    }

    @Test
    fun `refuses a line item with no description`() {
        assertFailsWith<IllegalArgumentException> { LineItem(" ", MoneyEur.ofEuros(100), 1) }
    }
}
