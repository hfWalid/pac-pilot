package fr.pacpilot.server.platform.adapter.out.document;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

/**
 * A downward-writing cursor over an A4 page, with just enough table support for two documents.
 *
 * <p>This class is the cost of ADR-0018, paid deliberately. Choosing a pure-JVM library over a
 * headless browser bought determinism and gave up CSS, so layout is code — and this is the code.
 * It is small on purpose: two documents need a heading, a paragraph, a key-value line, a table and
 * a page break, and inventing a layout engine beyond that would be building a renderer to avoid
 * having chosen one.
 *
 * <p>Standard-14 fonts only, never embedded (ADR-0018).
 */
final class PdfCanvas implements AutoCloseable {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 50f;
    private static final float TOP = PAGE.getHeight() - MARGIN;
    private static final float BOTTOM = MARGIN + 30f;
    private static final float LINE = 14f;

    static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private static final PDColor MUTED = new PDColor(new float[] {0.35f, 0.35f, 0.35f}, PDDeviceRGB.INSTANCE);
    private static final PDColor BLACK = new PDColor(new float[] {0f, 0f, 0f}, PDDeviceRGB.INSTANCE);

    private final PDDocument document;
    private PDPageContentStream content;
    private float cursor;

    PdfCanvas(PDDocument document) throws IOException {
        this.document = document;
        newPage();
    }

    float width() {
        return PAGE.getWidth() - 2 * MARGIN;
    }

    void heading(String text) throws IOException {
        space(6f);
        write(text, MARGIN, BOLD, 14f, BLACK);
        space(4f);
    }

    void subheading(String text) throws IOException {
        space(4f);
        write(text, MARGIN, BOLD, 10f, BLACK);
    }

    void line(String text) throws IOException {
        write(text, MARGIN, REGULAR, 9f, BLACK);
    }

    void muted(String text) throws IOException {
        write(text, MARGIN, ITALIC, 8f, MUTED);
    }

    /** A label on the left and its value on the right — the shape most of both documents takes. */
    void keyValue(String label, String value) throws IOException {
        ensureRoom();
        writeAt(label, MARGIN, cursor, REGULAR, 9f, BLACK);
        writeAt(value, MARGIN + 200f, cursor, BOLD, 9f, BLACK);
        cursor -= LINE;
    }

    /** One row across the given column offsets. The last column is right-ish aligned by offset. */
    void row(List<String> cells, float[] columns, boolean bold) throws IOException {
        ensureRoom();
        for (int index = 0; index < cells.size() && index < columns.length; index++) {
            writeAt(cells.get(index), MARGIN + columns[index], cursor, bold ? BOLD : REGULAR, 9f, BLACK);
        }
        cursor -= LINE;
    }

    void rule() throws IOException {
        ensureRoom();
        content.setStrokingColor(MUTED);
        content.moveTo(MARGIN, cursor + 4f);
        content.lineTo(PAGE.getWidth() - MARGIN, cursor + 4f);
        content.stroke();
        cursor -= 8f;
    }

    /** A boxed notice — how a provisional document says so unmissably (PAC-63, PAC-64). */
    void notice(String text) throws IOException {
        ensureRoom();
        content.setNonStrokingColor(new PDColor(new float[] {0.98f, 0.93f, 0.76f}, PDDeviceRGB.INSTANCE));
        content.addRect(MARGIN, cursor - 4f, width(), LINE + 6f);
        content.fill();
        content.setNonStrokingColor(BLACK);
        writeAt(text, MARGIN + 6f, cursor + 2f, BOLD, 9f, BLACK);
        cursor -= LINE + 12f;
    }

    void space(float points) {
        cursor -= points;
    }

    private void write(String text, float x, PDType1Font font, float size, PDColor colour)
            throws IOException {
        ensureRoom();
        writeAt(text, x, cursor, font, size, colour);
        cursor -= LINE;
    }

    private void writeAt(String text, float x, float y, PDType1Font font, float size, PDColor colour)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.setNonStrokingColor(colour);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private void ensureRoom() throws IOException {
        if (cursor < BOTTOM) {
            content.close();
            newPage();
        }
    }

    private void newPage() throws IOException {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);
        content = new PDPageContentStream(document, page);
        cursor = TOP;
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
