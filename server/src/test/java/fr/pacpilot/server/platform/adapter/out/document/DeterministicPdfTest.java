package fr.pacpilot.server.platform.adapter.out.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/**
 * The determinism foundation, tested before any layout is built on it (PAC-66).
 *
 * <p>Written first on purpose: if the renderer could not be made deterministic, that is a finding
 * for ADR-0018 rather than something to discover after two document templates exist.
 */
class DeterministicPdfTest {

    private static final LocalDate DEVIS_DATE = LocalDate.of(2026, 8, 22);

    private byte[] aDocument(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            return DeterministicPdf.finish(document, DEVIS_DATE, "devis-1");
        }
    }

    @Test
    void thesameDocumentGeneratedTwiceIsByteIdentical() throws IOException {
        assertThat(aDocument("Devis")).isEqualTo(aDocument("Devis"));
    }

    @Test
    void differentContentStillProducesDifferentBytes() {
        // Guards the assertion above from passing vacuously — a renderer that emitted a constant
        // would satisfy byte-identity perfectly and render nothing.
        assertThat(catching(() -> aDocument("Devis"))).isNotEqualTo(catching(() -> aDocument("Rapport")));
    }

    @Test
    void frenchAccentsAndTheEuroSignSurviveTheStandard14Font() throws IOException {
        // ADR-0018 chose standard-14 Helvetica with no embedded font. WinAnsiEncoding has to cover
        // what French typography needs, or the choice was wrong.
        assertThat(aDocument("déperditions à 1 234,56 € — ça marche")).isNotEmpty();
    }

    private byte[] catching(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        }
    }

    private interface ThrowingSupplier {
        byte[] get() throws IOException;
    }
}
