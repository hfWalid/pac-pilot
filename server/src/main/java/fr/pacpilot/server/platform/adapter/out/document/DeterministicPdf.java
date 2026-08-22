package fr.pacpilot.server.platform.adapter.out.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * Produces PDFs that are byte-identical when regenerated (PAC-66, ADR-0018).
 *
 * <p>The requirement is not cosmetic: a devis regenerated in two years must be provably the same
 * document, or the audit chain that M1–M4 built into the data stops at the boundary of the artefact.
 *
 * <p><b>Every source of drift PAC-66 names is set here explicitly, and each is listed in
 * {@code docs/DOCUMENT-DETERMINISM.md} so a renderer upgrade has a checklist rather than a
 * mystery.</b>
 *
 * <ol>
 *   <li><b>Creation and modification dates</b> — derived from the document's own effective date, at
 *       midnight UTC. Never from a clock: the domain forbids clocks in computation, and a document
 *       stamped with the moment it was printed could never be regenerated identically.
 *   <li><b>Producer and Creator</b> — fixed strings. PDFBox writes its own name and version by
 *       default, so a library upgrade would otherwise change every byte of every document.
 *   <li><b>The document {@code /ID}</b> — PDFBox derives one from the current time when none is set.
 *       Set explicitly from the document's own identity.
 *   <li><b>Fonts</b> — standard-14 only, never embedded. Subsetting varies with glyph discovery
 *       order, so the risk is removed rather than mitigated (ADR-0018).
 * </ol>
 */
final class DeterministicPdf {

    /** Fixed, so a PDFBox upgrade does not rewrite every byte of every document ever issued. */
    private static final String PRODUCER = "pac-pilot";

    private DeterministicPdf() {}

    /**
     * Stamps the document with deterministic metadata and serialises it.
     *
     * @param effectiveDate the document's own date — the devis date, or the study's. The one honest
     *     source of a timestamp in a system with no clocks.
     * @param documentId stable identity for this document, used for the PDF {@code /ID}.
     */
    static byte[] finish(PDDocument document, LocalDate effectiveDate, String documentId)
            throws IOException {
        Calendar stamp = midnightUtcOn(effectiveDate);

        var information = document.getDocumentInformation();
        information.setCreationDate(stamp);
        information.setModificationDate(stamp);
        information.setProducer(PRODUCER);
        information.setCreator(PRODUCER);

        // PDFBox generates an /ID from the current time when the trailer has none. Two entries, both
        // the same value: the first identifies the document, the second the revision, and an
        // unmodified document has one revision.
        COSString identity = new COSString(documentId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        COSArray id = new COSArray();
        id.add(identity);
        id.add(identity);
        document.getDocument().getTrailer().setItem(org.apache.pdfbox.cos.COSName.ID, id);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        document.save(bytes);
        return bytes.toByteArray();
    }

    private static Calendar midnightUtcOn(LocalDate date) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
        calendar.clear();
        calendar.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth(), 0, 0, 0);
        return calendar;
    }
}
