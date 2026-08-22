package fr.pacpilot.server.platform.adapter.out.document;

import fr.pacpilot.core.dimensioning.model.Assumption;
import fr.pacpilot.core.dimensioning.model.Confidence;
import fr.pacpilot.core.dimensioning.model.InputsSnapshot;
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.server.dimensioning.application.port.out.RenderPreVisitReport;
import fr.pacpilot.server.platform.api.GeneratedDocument;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * The pre-visit technical report — the artefact that carries the technical defence (PAC-64).
 *
 * <p>Where the devis says what it costs, this says <b>how the sizing was arrived at</b>, which is
 * what a QualiPAC auditor or a decennial insurer actually asks about.
 *
 * <p>The assumptions log exists for this document. M2-06 made the engine record every coefficient it
 * applied, in application order, with each source — specifically so a human could follow the
 * reasoning. Here it stops being a data structure and becomes the thing an auditor reads.
 *
 * <p><b>Photo slots are present and empty</b>, so M9 drops images in without a re-layout.
 */
@Component
class PdfPreVisitReportRenderer implements RenderPreVisitReport {

    private static final float[] ASSUMPTION_COLUMNS = {0f, 20f, 300f};

    @Override
    public GeneratedDocument render(ValidatedDimensioning study) {
        try (PDDocument document = new PDDocument()) {
            try (PdfCanvas canvas = new PdfCanvas(document)) {
                writeReport(canvas, study);
            }
            var date = study.getEffectiveDate();
            return new GeneratedDocument(
                    DeterministicPdf.finish(
                            document,
                            LocalDate.of(date.getYear(), date.getMonth(), date.getDay()),
                            "rapport-" + study.getId().getValue()),
                    GeneratedDocument.PDF);
        } catch (IOException failure) {
            throw new IllegalStateException("could not render report " + study.getId().getValue(), failure);
        }
    }

    private void writeReport(PdfCanvas canvas, ValidatedDimensioning study) throws IOException {
        canvas.heading("RAPPORT DE VISITE TECHNIQUE PRÉALABLE");
        canvas.keyValue("Étude n°", study.getId().getValue());
        canvas.keyValue("Date d'effet", FrenchFormat.date(study.getEffectiveDate()));

        confidenceNotice(canvas, study);
        writeObservations(canvas, study.getInputs());
        writeResult(canvas, study);
        writeAssumptions(canvas, study.getResult().getAssumptions().getEntries());
        writeValidation(canvas, study);
        writePhotoSlots(canvas);
    }

    /**
     * Prominent, not buried in small print.
     *
     * <p>Until the ⚑ gate closes, every entry in the assumptions log is {@code SOURCE_TBD} — and the
     * report has to show that honestly. A report that looked authoritative while resting entirely on
     * placeholders is precisely the document that would end up in an insurer's file.
     */
    private void confidenceNotice(PdfCanvas canvas, ValidatedDimensioning study) throws IOException {
        if (study.getResult().getConfidence() == Confidence.INDICATIVE) {
            canvas.notice(
                    "CONFIANCE : INDICATIVE — au moins un coefficient n'est pas sourcé. "
                            + "Méthode non validée.");
        } else {
            canvas.keyValue("Confiance", "Étayée — tous les coefficients sont sourcés");
        }
    }

    private void writeObservations(PdfCanvas canvas, InputsSnapshot inputs) throws IOException {
        canvas.heading("Relevé sur site");
        canvas.keyValue("Surface habitable", FrenchFormat.withUnit(inputs.getSurface().render(), "m²"));
        canvas.keyValue(
                "Hauteur sous plafond", FrenchFormat.withUnit(inputs.getCeilingHeight().render(), "m"));
        canvas.keyValue("Période de construction", inputs.getConstructionPeriod().name());
        canvas.keyValue("Niveau d'isolation", inputs.getInsulationLevel().name());
        canvas.keyValue("Ventilation", inputs.getVentilationType().name());
        canvas.keyValue("Émetteurs", inputs.getEmitterType().name());
        canvas.keyValue("Zone climatique", inputs.getClimateZone().name());
        // Both the zone and the resolved temperature, per InputsSnapshot's own reasoning: the zone is
        // what the installer recognises, the resolved value is what the formula consumed.
        canvas.keyValue(
                "Température de base retenue",
                FrenchFormat.withUnit(inputs.getBaseTemperature().render(), "°C"));
        canvas.keyValue(
                "Température intérieure visée",
                FrenchFormat.withUnit(inputs.getTargetIndoorTemperature().render(), "°C"));
        canvas.keyValue(
                "Puissance électrique disponible",
                inputs.getAvailableElectricalPower().getKva() + FrenchFormat.NBSP + "kVA");
    }

    private void writeResult(PdfCanvas canvas, ValidatedDimensioning study) throws IOException {
        var result = study.getResult();
        canvas.heading("Résultat du dimensionnement");
        canvas.keyValue("Déperditions calculées", FrenchFormat.withUnit(result.getHeatLoad().render(), "kW"));
        canvas.keyValue(
                "Plage de puissance recommandée",
                FrenchFormat.withUnit(result.getRecommendedPowerBand().getMinimum().render(), "kW")
                        + " – "
                        + FrenchFormat.withUnit(result.getRecommendedPowerBand().getMaximum().render(), "kW"));

        // A withheld loi d'eau must read as a decision, not an omission. M2-05 records the reason in
        // the log precisely so a reader can tell the method declined from the method forgetting; a
        // blank field here would throw that distinction away.
        if (result.getRecommendedFlowTemperature() == null) {
            canvas.keyValue("Loi d'eau", "Aucune préconisation validée pour ces émetteurs");
        } else {
            canvas.keyValue(
                    "Loi d'eau",
                    FrenchFormat.withUnit(result.getRecommendedFlowTemperature().render(), "°C"));
        }
    }

    /** Rendered for a person, in the order the method made them, each with its source. */
    private void writeAssumptions(PdfCanvas canvas, List<Assumption> assumptions) throws IOException {
        canvas.heading("Hypothèses retenues");
        canvas.muted("Dans l'ordre où la méthode les a appliquées.");
        canvas.space(4f);
        canvas.row(List.of("#", "Hypothèse", "Source"), ASSUMPTION_COLUMNS, true);
        canvas.rule();

        int position = 1;
        for (Assumption assumption : assumptions) {
            canvas.row(
                    List.of(
                            Integer.toString(position++),
                            assumption.getStatement(),
                            assumption.getSource()),
                    ASSUMPTION_COLUMNS,
                    false);
        }
    }

    private void writeValidation(PdfCanvas canvas, ValidatedDimensioning study) throws IOException {
        // The legal shield (§4.5): who took responsibility, and when — distinct from when it was
        // computed. An auditor asks about the second.
        var validation = study.getValidation();
        canvas.heading("Validation");
        canvas.keyValue("Validé par", validation.getValidatedBy().getValue());
        canvas.keyValue("Le", FrenchFormat.instant(validation.getValidatedAt().getEpochMilliseconds()));
        canvas.muted(
                "Le professionnel valide ce dimensionnement et l'engage sous sa responsabilité. "
                        + "L'outil est une aide à la décision.");
    }

    /** Empty slots that hold their space, so M9 is additive rather than a re-layout. */
    private void writePhotoSlots(PdfCanvas canvas) throws IOException {
        canvas.heading("Photographies du site");
        canvas.muted("Emplacements réservés — horodatées et géolocalisées, ajoutées à la synchronisation.");
        for (int slot = 1; slot <= 4; slot++) {
            canvas.keyValue("Photo " + slot, "—");
        }
    }
}
