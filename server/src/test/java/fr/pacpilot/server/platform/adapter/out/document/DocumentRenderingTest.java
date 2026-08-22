package fr.pacpilot.server.platform.adapter.out.document;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.aids.model.AidLine;
import fr.pacpilot.core.aids.model.AidRuleId;
import fr.pacpilot.core.aids.model.AidRulePackVersion;
import fr.pacpilot.core.aids.model.ResolvedAids;
import fr.pacpilot.core.dimensioning.model.Assumption;
import fr.pacpilot.core.dimensioning.model.AssumptionsLog;
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod;
import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome;
import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.dimensioning.model.HeatLoadResult;
import fr.pacpilot.core.dimensioning.model.InputsSnapshot;
import fr.pacpilot.core.dimensioning.model.InsulationLevel;
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.core.dimensioning.model.VentilationType;
import fr.pacpilot.core.quoting.model.LineItem;
import fr.pacpilot.core.quoting.model.ProductSnapshot;
import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.core.shared.CeilingHeightM;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.DimensioningId;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
import fr.pacpilot.core.shared.InstallerId;
import fr.pacpilot.core.shared.InstantUtc;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import fr.pacpilot.core.shared.PowerBand;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.ProductId;
import fr.pacpilot.core.shared.QuoteId;
import fr.pacpilot.core.shared.SiteId;
import fr.pacpilot.core.shared.SurfaceM2;
import fr.pacpilot.core.shared.TemperatureC;
import fr.pacpilot.server.platform.api.GeneratedDocument;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * The two documents, read as their audiences read them.
 *
 * <p>Assertions are on the <b>extracted text of the rendered PDF</b>, never on the template source.
 * A template that mentions the pack version proves nothing; a PDF whose text contains it proves the
 * audit chain reached the page.
 */
class DocumentRenderingTest {

    private final PdfDevisRenderer devisRenderer = new PdfDevisRenderer();
    private final PdfPreVisitReportRenderer reportRenderer = new PdfPreVisitReportRenderer();

    private static final AidRulePackVersion PACK = new AidRulePackVersion("sample-2025-H1");
    private static final EffectiveDate DEVIS_DATE = new EffectiveDate(2026, 8, 22);
    private static final InstallerId SIGNER = new InstallerId("installer-42");

    private static final AssumptionsLog PROVISIONAL_LOG =
            new AssumptionsLog(
                    List.of(
                            new Assumption("Coefficient U issu de la periode de construction", "SOURCE_TBD"),
                            new Assumption("Surface deperditive estimee depuis la surface au sol", "SOURCE_TBD"),
                            new Assumption("Taux de renouvellement d'air issu de la ventilation", "SOURCE_TBD")));

    private static final AssumptionsLog SOURCED_LOG =
            new AssumptionsLog(List.of(new Assumption("Coefficient U", "3CL-DPE 2021, tableau 4")));

    private ValidatedDimensioning study(AssumptionsLog log, TemperatureC flowTemperature) {
        return Dimensioning.Companion.computed(
                        new DimensioningId("dim-1"),
                        new SiteId("site-1"),
                        new InputsSnapshot(
                                new SurfaceM2(12_000),
                                new CeilingHeightM(250),
                                ConstructionPeriod.BEFORE_1975,
                                InsulationLevel.PARTIAL,
                                VentilationType.VMC_SIMPLE_FLUX,
                                EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                ClimateZone.H1,
                                new TemperatureC(-70),
                                new TemperatureC(190),
                                new ElectricalSupplyKva(9)),
                        new DimensioningOutcome.Computed(
                                new HeatLoadResult(
                                        new PowerKw(19_032),
                                        new PowerBand(new PowerKw(17_129), new PowerKw(22_838)),
                                        flowTemperature,
                                        log)),
                        new EffectiveDate(2026, 8, 20))
                .validate(SIGNER, new InstantUtc(java.time.Instant.parse("2026-08-20T14:30:00Z").toEpochMilli()));
    }

    private Quote devis(ResolvedAids aids, AssumptionsLog log) {
        return Quote.Companion.draft(
                new QuoteId("quote-1"),
                study(log, new TemperatureC(500)),
                new ProductSnapshot(
                        new ProductId("echantillon-12"),
                        "ECHANTILLON 12 kW",
                        new PowerKw(12_000),
                        MoneyEur.Companion.ofEuros(9_500)),
                List.of(
                        new LineItem("PAC air-eau 12 kW", MoneyEur.Companion.ofEuros(9_500), 1, new Percentage(550)),
                        new LineItem("Pose et mise en service", MoneyEur.Companion.ofEuros(2_500), 1, new Percentage(550)),
                        new LineItem("Radiateur basse temperature", MoneyEur.Companion.ofEuros(500), 4, new Percentage(550))),
                aids,
                DEVIS_DATE);
    }

    private ResolvedAids twoAids() {
        return new ResolvedAids(
                PACK,
                List.of(
                        new AidLine(new AidRuleId("mpr"), "MaPrimeRenov", MoneyEur.Companion.ofEuros(4_000), "anah.gouv.fr"),
                        new AidLine(new AidRuleId("cee"), "CEE", MoneyEur.Companion.ofEuros(500), "fiche BAR-TH-171")));
    }

    private String textOf(GeneratedDocument document) throws IOException {
        try (var pdf = Loader.loadPDF(document.content())) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    // ── The devis (PAC-63) ───────────────────────────────────────────────────────────────────

    @Test
    void theDevisCarriesItsAuditChainOnThePage() throws IOException {
        String text = textOf(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG)));

        // Which machine — from the stored snapshot, not a catalogue lookup.
        assertThat(text).contains("ECHANTILLON 12 kW", "12,000" + FrenchFormat.NBSP + "kW");
        // Which barème priced it. This line is what makes the document reproducible in three years.
        assertThat(text).contains("sample-2025-H1");
        // Which aid came from where.
        assertThat(text).contains("MaPrimeRenov", "anah.gouv.fr", "CEE", "fiche BAR-TH-171");
    }

    @Test
    void everyTotalOnThePageIsTheOneTheAggregateDerived() throws IOException {
        // 9 500 + 2 500 + (500 x 4) = 14 000 HT; TVA 5,5 % = 770,00; TTC = 14 770,00;
        // less 4 500 in aids = 10 270,00. A template that added the lines up itself would be a
        // second source of truth for the number the client reacts to.
        Quote quote = devis(twoAids(), PROVISIONAL_LOG);
        String text = textOf(devisRenderer.render(quote));

        assertThat(text).contains(FrenchFormat.money(quote.getSubtotalExcludingVat()));
        assertThat(text).contains(FrenchFormat.money(quote.getVat()));
        assertThat(text).contains(FrenchFormat.money(quote.getTotalIncludingVat()));
        assertThat(text).contains(FrenchFormat.money(quote.getResteACharge().getAmount()));
        assertThat(text).contains("14" + FrenchFormat.NBSP + "770,00");
    }

    @Test
    void aProvisionalDevisSaysSoUnmissably() throws IOException {
        String provisional = textOf(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG)));
        assertThat(provisional).contains("ESTIMATION INDICATIVE");

        String sourced = textOf(devisRenderer.render(devis(twoAids(), SOURCED_LOG)));
        assertThat(sourced).doesNotContain("ESTIMATION INDICATIVE");
    }

    @Test
    void anAbsenceOfAidsReadsAsARefusalRatherThanAsZero() throws IOException {
        // Zero is a claim about the household; an absence is a statement about the system. Today no
        // barème is published at all (ADR-0017).
        String text = textOf(devisRenderer.render(devis(ResolvedAids.Companion.none(PACK), PROVISIONAL_LOG)));

        assertThat(text).contains("Aucune aide n'a pu être calculée");
        assertThat(text).contains("sample-2025-H1");
    }

    @Test
    void anOverGrantedDevisFlagsRatherThanPrintingANegativeFigureBare() throws IOException {
        ResolvedAids tooMuch =
                new ResolvedAids(
                        PACK,
                        List.of(new AidLine(new AidRuleId("mpr"), "MaPrimeRenov", MoneyEur.Companion.ofEuros(90_000), "anah.gouv.fr")));

        String text = textOf(devisRenderer.render(devis(tooMuch, PROVISIONAL_LOG)));

        assertThat(text).contains("ANOMALIE");
        assertThat(text).contains("doit être vérifié");
    }

    @Test
    void theDevisCarriesTheAideALaDecisionFraming() throws IOException {
        // §4.5's liability position, on the artefact rather than only in the model.
        assertThat(textOf(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG))))
                .contains("aide à la décision");
    }

    // ── The pre-visit report (PAC-64) ────────────────────────────────────────────────────────

    @Test
    void theReportShowsHowTheSizingWasReached() throws IOException {
        String text = textOf(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))));

        assertThat(text).contains("120,00", "2,50", "BEFORE_1975", "PARTIAL", "VMC_SIMPLE_FLUX");
        assertThat(text).contains("-7,0", "19,032");
    }

    @Test
    void theAssumptionsLogIsRenderedInOrderWithEverySource() throws IOException {
        String text = textOf(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))));

        int first = text.indexOf("Coefficient U issu");
        int second = text.indexOf("Surface deperditive");
        int third = text.indexOf("Taux de renouvellement");

        assertThat(first).isGreaterThan(-1);
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
        assertThat(text).contains("SOURCE_TBD");
    }

    @Test
    void withheldLoiDEauReadsAsADecisionRatherThanAnOmission() throws IOException {
        // M2-05 records the reason in the log precisely so a reader can tell the method declined
        // from the method forgetting. A blank field would lose that.
        String text = textOf(reportRenderer.render(study(PROVISIONAL_LOG, null)));

        assertThat(text).contains("Aucune préconisation validée");
    }

    @Test
    void theReportNamesWhoSignedAndWhenInTheArtisansTimeZone() throws IOException {
        String text = textOf(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))));

        assertThat(text).contains("installer-42");
        assertThat(text).contains("20/08/2026 à 16:30");
    }

    @Test
    void photoSlotsRenderEmptySoThatM9IsAdditive() throws IOException {
        String text = textOf(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))));

        assertThat(text).contains("Photographies du site", "Photo 1", "Photo 4");
    }

    @Test
    void anIndicativeReportSaysSoProminently() throws IOException {
        assertThat(textOf(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500)))))
                .contains("CONFIANCE : INDICATIVE");
        assertThat(textOf(reportRenderer.render(study(SOURCED_LOG, new TemperatureC(500)))))
                .contains("Étayée");
    }

    // ── Sensitive data (M3-08, §4.6) ─────────────────────────────────────────────────────────

    @Test
    void noIncomeDecileAppearsOnEitherDocument() throws IOException {
        // The aid amount belongs on the page; the tier that produced it does not. Neither document
        // is even given a decile — asserted so a future template cannot quietly acquire one.
        String devisText = textOf(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG)));
        String reportText = textOf(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))));

        assertThat(devisText.toLowerCase()).doesNotContain("décile", "decile", "revenu");
        assertThat(reportText.toLowerCase()).doesNotContain("décile", "decile", "revenu");
    }

    // ── Determinism (PAC-66) ─────────────────────────────────────────────────────────────────

    @Test
    void theSameDevisRendersToIdenticalBytes() {
        assertThat(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG)))
                .isEqualTo(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG)));
    }

    @Test
    void theSameReportRendersToIdenticalBytes() {
        assertThat(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))))
                .isEqualTo(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500))));
    }

    /**
     * The across-time claim, which the two in-run assertions above cannot make.
     *
     * <p>Two generations inside one JVM share a clock, a classpath and a PDFBox version, so they
     * agree even when something drifts — this was proven the hard way while building the
     * determinism foundation, where a deliberately broken timestamp did not fail the in-run test.
     *
     * <p>A pinned hash is the artefact-level twin of the golden vectors: recorded once, asserted
     * forever. If it moves, something changed the bytes — a PDFBox upgrade, a template edit, a
     * locale leak — and that is exactly when someone should be reading
     * {@code docs/DOCUMENT-DETERMINISM.md} rather than shrugging.
     *
     * <p><b>A failure here is not automatically a defect.</b> A deliberate template change moves the
     * hash legitimately. What it must never be is a surprise.
     */
    @Test
    void theRenderedDocumentsStillHashToTheirRecordedValues() {
        assertThat(sha256(devisRenderer.render(devis(twoAids(), PROVISIONAL_LOG))))
                .as("the devis bytes moved — see docs/DOCUMENT-DETERMINISM.md before updating this")
                .isEqualTo("b9c2f28cc7384d7488b886055f01bfcc76bbaf08cc94c8d253fcb4b5932c3858");

        assertThat(sha256(reportRenderer.render(study(PROVISIONAL_LOG, new TemperatureC(500)))))
                .as("the report bytes moved — see docs/DOCUMENT-DETERMINISM.md before updating this")
                .isEqualTo("29a39a9f412f7b70797578785e973873b6dcd6d26b68b934d2f19b4b5431c315");
    }

    // ── Client path vs server path (PAC-67) ──────────────────────────────────────────────────

    /**
     * The point where two guarantees meet.
     *
     * <p>The golden vectors prove the JVM and JS targets compute identically — 29 of them, both
     * targets, every build. The pinned hash proves the renderer is stable across time. This proves
     * they <b>compose</b>: a figure calculated in a cellar with no signal, synced hours later,
     * produces the same document as one calculated on the server.
     *
     * <p>If they diverged, the installer showed the homeowner one number and the file contains
     * another — the exact failure the client-computes-server-verifies architecture exists to
     * prevent, arriving at the last possible moment.
     *
     * <p><b>The limitation, stated:</b> there is no JS runtime in this suite and no offline client
     * until M7, so the "client path" is simulated by building the result from the values the JS
     * target is proven to produce — the published golden-vector expectations for
     * {@code dimensioning-period-before-1975-001} — rather than by running JS here. What this test
     * therefore proves precisely is that <b>rendering is a pure function of the aggregate</b>: two
     * aggregates carrying identical values render to identical bytes regardless of which side
     * computed them. Given the vectors, that is the whole of the remaining claim.
     */
    @Test
    void aDevisComputedOnTheClientRendersIdenticallyToOneComputedOnTheServer() {
        // As the JS target produces it, per the published golden vector.
        HeatLoadResult fromTheDevice =
                new HeatLoadResult(
                        new PowerKw(19_032),
                        new PowerBand(new PowerKw(17_129), new PowerKw(22_838)),
                        new TemperatureC(500),
                        PROVISIONAL_LOG);

        // As the JVM engine produces it, through the same provisional method the server runs.
        HeatLoadResult fromTheServer =
                new HeatLoadResult(
                        new PowerKw(19_032),
                        new PowerBand(new PowerKw(17_129), new PowerKw(22_838)),
                        new TemperatureC(500),
                        PROVISIONAL_LOG);

        assertThat(devisRenderer.render(devisAround(fromTheDevice)))
                .isEqualTo(devisRenderer.render(devisAround(fromTheServer)));
    }

    @Test
    void aOneCentDifferenceBetweenThePathsIsCaught() {
        // A test that cannot be made to fail is not protecting the guarantee. One cent on the
        // machine price is the smallest divergence the two paths could plausibly produce.
        Quote asShown = devis(twoAids(), PROVISIONAL_LOG);
        Quote aCentOff =
                Quote.Companion.draft(
                        asShown.getId(),
                        study(PROVISIONAL_LOG, new TemperatureC(500)),
                        asShown.getProduct(),
                        List.of(
                                new LineItem("PAC air-eau 12 kW", new MoneyEur(949_999), 1, new Percentage(550)),
                                new LineItem("Pose et mise en service", MoneyEur.Companion.ofEuros(2_500), 1, new Percentage(550)),
                                new LineItem("Radiateur basse temperature", MoneyEur.Companion.ofEuros(500), 4, new Percentage(550))),
                        twoAids(),
                        DEVIS_DATE);

        assertThat(devisRenderer.render(asShown)).isNotEqualTo(devisRenderer.render(aCentOff));
    }

    /** The same devis, around a heat-load result from whichever side computed it. */
    private Quote devisAround(HeatLoadResult result) {
        ValidatedDimensioning computed =
                Dimensioning.Companion.computed(
                                new DimensioningId("dim-1"),
                                new SiteId("site-1"),
                                new InputsSnapshot(
                                        new SurfaceM2(12_000),
                                        new CeilingHeightM(250),
                                        ConstructionPeriod.BEFORE_1975,
                                        InsulationLevel.PARTIAL,
                                        VentilationType.VMC_SIMPLE_FLUX,
                                        EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                        ClimateZone.H1,
                                        new TemperatureC(-70),
                                        new TemperatureC(190),
                                        new ElectricalSupplyKva(9)),
                                new DimensioningOutcome.Computed(result),
                                new EffectiveDate(2026, 8, 20))
                        .validate(SIGNER, new InstantUtc(java.time.Instant.parse("2026-08-20T14:30:00Z").toEpochMilli()));

        return Quote.Companion.draft(
                new QuoteId("quote-1"),
                computed,
                new ProductSnapshot(
                        new ProductId("echantillon-12"),
                        "ECHANTILLON 12 kW",
                        new PowerKw(12_000),
                        MoneyEur.Companion.ofEuros(9_500)),
                List.of(new LineItem("PAC air-eau 12 kW", MoneyEur.Companion.ofEuros(9_500), 1, new Percentage(550))),
                twoAids(),
                DEVIS_DATE);
    }

    static String sha256(GeneratedDocument document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.content()));
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }
}
