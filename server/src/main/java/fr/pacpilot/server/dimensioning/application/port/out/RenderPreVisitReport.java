package fr.pacpilot.server.dimensioning.application.port.out;

import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.server.platform.api.GeneratedDocument;

/**
 * Driven port — render the pre-visit technical report.
 *
 * <p>Takes a {@link ValidatedDimensioning} rather than the sealed supertype: the report carries the
 * validation act — who signed and when, distinct from when it was computed ({@code CLAUDE.md} §4.5)
 * — so an unsigned study has nothing to put in that section. Requiring the signed case makes that a
 * compile-time fact instead of a null check in a template.
 */
public interface RenderPreVisitReport {

    GeneratedDocument render(ValidatedDimensioning study);
}
