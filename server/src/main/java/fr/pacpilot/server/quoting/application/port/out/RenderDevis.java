package fr.pacpilot.server.quoting.application.port.out;

import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.server.platform.api.GeneratedDocument;

/**
 * Driven port — render a devis as a document.
 *
 * <p><b>Takes the aggregate, not a template name and not a model map.</b> A
 * {@code render(templateName, model)} shape is a renderer's API wearing a port's clothes: it lets
 * template concerns leak upward, and it makes the application layer responsible for knowing which
 * template exists.
 *
 * <p>Everything the page needs is already on {@link Quote} — the product snapshot, the aid lines
 * with their pack version, the per-line VAT rates and the derived totals. The adapter reads; it
 * never recomputes.
 *
 * <p>Separate from {@link fr.pacpilot.server.dimensioning.application.port.out.RenderPreVisitReport}
 * rather than one port with a discriminator: they take different aggregates and answer to different
 * readers, and a flag parameter would only postpone that fact.
 */
public interface RenderDevis {

    GeneratedDocument render(Quote devis);
}
