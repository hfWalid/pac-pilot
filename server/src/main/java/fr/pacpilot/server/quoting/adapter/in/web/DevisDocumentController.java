package fr.pacpilot.server.quoting.adapter.in.web;

import fr.pacpilot.server.platform.api.GeneratedDocument;
import fr.pacpilot.server.quoting.application.port.out.QuoteRepository;
import fr.pacpilot.server.quoting.application.port.out.RenderDevis;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fetching the devis as a PDF.
 *
 * <p>Added so the M5 renderer is reachable: a document nothing can fetch has not left the system,
 * and an adapter nothing calls is a capability that has never been exercised end to end.
 *
 * <p><b>Rendered on demand, never stored.</b> Storage is M9's object-storage concern. Regenerating
 * is safe precisely because it is deterministic (PAC-66) — the bytes fetched today are the bytes
 * fetched in three years, which is what makes the document evidence rather than a re-render.
 */
@RestController
@RequestMapping("/api/quotes")
class DevisDocumentController {

    private final QuoteRepository quotes;
    private final RenderDevis renderer;

    DevisDocumentController(QuoteRepository quotes, RenderDevis renderer) {
        this.quotes = quotes;
        this.renderer = renderer;
    }

    @GetMapping(value = "/{id}/document", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> devis(@PathVariable UUID id) {
        return quotes.findById(id)
                .map(renderer::render)
                .map(document -> attachment(document, "devis-" + id + ".pdf"))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    static ResponseEntity<byte[]> attachment(GeneratedDocument document, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(document.content());
    }
}
