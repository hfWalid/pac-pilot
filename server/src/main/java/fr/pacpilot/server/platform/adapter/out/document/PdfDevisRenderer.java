package fr.pacpilot.server.platform.adapter.out.document;

import fr.pacpilot.core.aids.model.AidLine;
import fr.pacpilot.core.dimensioning.model.Confidence;
import fr.pacpilot.core.quoting.model.LineItem;
import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.server.platform.api.GeneratedDocument;
import fr.pacpilot.server.quoting.application.port.out.RenderDevis;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * The devis, as the document that leaves the system (PAC-63).
 *
 * <p><b>Every total is read from the aggregate, never recomputed here.</b> {@code Quote} derives
 * {@code subtotalExcludingVat}, {@code vat}, {@code totalIncludingVat} and {@code resteACharge}; a
 * template that added up the lines itself would be a second source of truth for the number the
 * client reacts to, and the two would part company the first time a line was corrected.
 *
 * <p><b>The audit chain is on the page.</b> PRODUCT-VIEWS #8 requires it: a devis whose provenance
 * lives only in a database is not defensible when someone is holding the paper. So the machine comes
 * from the stored {@code ProductSnapshot} rather than a live catalogue lookup, each aid names the
 * rule and source it came from, and the rule-pack version is printed.
 *
 * <p>Lives in {@code platform} rather than in {@code quoting}: rendering is a cross-cutting technical
 * capability, and {@code platform} is the composition root that may see other contexts. The port
 * stays in {@code quoting}, which is what owns the need.
 */
@Component
class PdfDevisRenderer implements RenderDevis {

    private static final float[] LINE_COLUMNS = {0f, 250f, 300f, 370f, 430f};
    private static final float[] AID_COLUMNS = {0f, 250f, 370f};

    @Override
    public GeneratedDocument render(Quote devis) {
        try (PDDocument document = new PDDocument()) {
            try (PdfCanvas canvas = new PdfCanvas(document)) {
                writeDevis(canvas, devis);
            }
            var date = devis.getEffectiveDate();
            return new GeneratedDocument(
                    DeterministicPdf.finish(
                            document,
                            LocalDate.of(date.getYear(), date.getMonth(), date.getDay()),
                            "devis-" + devis.getId().getValue()),
                    GeneratedDocument.PDF);
        } catch (IOException failure) {
            // Rendering a document that is already in memory should not fail on I/O. If it does,
            // something is wrong with the renderer rather than with this devis.
            throw new IllegalStateException("could not render devis " + devis.getId().getValue(), failure);
        }
    }

    private void writeDevis(PdfCanvas canvas, Quote devis) throws IOException {
        canvas.heading("DEVIS");
        canvas.keyValue("Numéro", devis.getId().getValue());
        canvas.keyValue("Date", FrenchFormat.date(devis.getEffectiveDate()));
        canvas.keyValue("Statut", devis.getStatus().name());

        provisionalNotice(canvas, devis);

        canvas.heading("Matériel proposé");
        var product = devis.getProduct();
        // From the stored snapshot, never a catalogue lookup: a machine discontinued next year must
        // not rewrite a devis issued last year.
        canvas.keyValue("Modèle", product.getModel());
        canvas.keyValue(
                // ASCII hyphen, not U+2212: the standard-14 fonts of ADR-0018 use WinAnsiEncoding,
                // which has no typographic minus. PDFBox refuses it at render time rather than
                // dropping it, which is the right failure but a poor place to discover it.
                "Puissance à -7 °C", FrenchFormat.withUnit(product.getPowerAtMinusSevenC().render(), "kW"));

        canvas.heading("Prestations");
        canvas.row(List.of("Désignation", "Qté", "P.U. HT", "TVA", "Total HT"), LINE_COLUMNS, true);
        canvas.rule();
        for (LineItem item : devis.getLines()) {
            canvas.row(
                    List.of(
                            item.getLabel(),
                            Integer.toString(item.getQuantity()),
                            FrenchFormat.money(item.getUnitPrice()),
                            FrenchFormat.percentage(item.getVatRate()),
                            FrenchFormat.money(item.getTotal())),
                    LINE_COLUMNS,
                    false);
        }
        canvas.rule();
        canvas.keyValue("Total HT", FrenchFormat.money(devis.getSubtotalExcludingVat()));
        canvas.keyValue("TVA", FrenchFormat.money(devis.getVat()));
        canvas.keyValue("Total TTC", FrenchFormat.money(devis.getTotalIncludingVat()));

        writeAids(canvas, devis);
        writeResteACharge(canvas, devis);
        writeLegalMentions(canvas);
    }

    /**
     * A provisional result says so on the page, in a box, before anything else is read.
     *
     * <p>While the ⚑ method gate (PAC-42) is open every study is {@code INDICATIVE}, and a
     * placeholder figure that looks authoritative in print is the failure mode this whole product is
     * organised against ({@code CLAUDE.md} §12).
     */
    private void provisionalNotice(PdfCanvas canvas, Quote devis) throws IOException {
        if (devis.getDimensioning().getResult().getConfidence() == Confidence.INDICATIVE) {
            canvas.notice(
                    "ESTIMATION INDICATIVE — méthode de dimensionnement non validée. "
                            + "Ne pas utiliser comme engagement.");
        }
    }

    private void writeAids(PdfCanvas canvas, Quote devis) throws IOException {
        canvas.heading("Aides déduites");
        var aids = devis.getResolvedAids();

        if (aids.getLines().isEmpty()) {
            // Not "0 €": zero is a claim about the household, an absence is a statement about the
            // system. Today no barème is published at all (ADR-0017).
            canvas.line("Aucune aide n'a pu être calculée pour cette date.");
        } else {
            canvas.row(List.of("Dispositif", "Référence", "Montant"), AID_COLUMNS, true);
            canvas.rule();
            for (AidLine aid : aids.getLines()) {
                canvas.row(
                        List.of(aid.getLabel(), aid.getSource(), FrenchFormat.money(aid.getAmount())),
                        AID_COLUMNS,
                        false);
            }
            canvas.rule();
            canvas.keyValue("Total des aides", FrenchFormat.money(aids.getTotal()));
        }

        // The audit chain, printed. Discreet, but present and legible — it is what makes this
        // document reproducible three years from now.
        canvas.muted("Barème appliqué : version " + aids.getPackVersion().getValue());
    }

    private void writeResteACharge(PdfCanvas canvas, Quote devis) throws IOException {
        var resteACharge = devis.getResteACharge();
        canvas.heading("Reste à charge");

        if (resteACharge.isOverGranted()) {
            // Deliberate rather than printing a negative figure bare. Aids exceeding the cost means
            // a barème was misapplied (M1-07), and the page says so instead of looking plausible.
            canvas.notice("ANOMALIE — les aides calculées dépassent le montant des travaux.");
            canvas.keyValue("Écart", FrenchFormat.money(resteACharge.getAmount()));
            canvas.line("Ce devis doit être vérifié avant d'être remis au client.");
        } else {
            canvas.keyValue("Reste à charge", FrenchFormat.money(resteACharge.getAmount()));
        }
    }

    /** The aide-à-la-décision framing of §4.5, which is the liability position in one paragraph. */
    private void writeLegalMentions(PdfCanvas canvas) throws IOException {
        canvas.heading("Mentions");
        canvas.muted(
                "Ce document est une aide à la décision. Les résultats sont des propositions que le "
                        + "professionnel valide et engage sous sa responsabilité.");
        canvas.muted(
                "Les montants d'aides sont des estimations calculées selon le barème en vigueur à la "
                        + "date du devis. Ils ne valent pas accord de l'organisme financeur.");
        canvas.muted("Devis établi sous réserve de visite technique préalable.");
    }
}
