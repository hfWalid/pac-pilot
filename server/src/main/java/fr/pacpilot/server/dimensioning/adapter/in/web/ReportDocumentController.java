package fr.pacpilot.server.dimensioning.adapter.in.web;

import fr.pacpilot.server.dimensioning.api.ValidatedStudies;
import fr.pacpilot.server.dimensioning.application.port.out.RenderPreVisitReport;
import fr.pacpilot.server.platform.api.GeneratedDocument;
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
 * Fetching the pre-visit technical report as a PDF.
 *
 * <p>Goes through {@link ValidatedStudies} rather than the repository: the report carries the
 * validation act, so an unsigned study has nothing to put in that section. A 404 for an unsigned
 * study is the honest answer — the report does not exist yet.
 */
@RestController
@RequestMapping("/api/dimensionings")
class ReportDocumentController {

    private final ValidatedStudies studies;
    private final RenderPreVisitReport renderer;

    ReportDocumentController(ValidatedStudies studies, RenderPreVisitReport renderer) {
        this.studies = studies;
        this.renderer = renderer;
    }

    @GetMapping(value = "/{id}/report", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> report(@PathVariable UUID id) {
        return studies.findValidated(id)
                .map(renderer::render)
                .map(document -> attachment(document, "rapport-" + id + ".pdf"))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> attachment(GeneratedDocument document, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(document.content());
    }
}
