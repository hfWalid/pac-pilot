package fr.pacpilot.server.quoting.application;

import fr.pacpilot.core.aids.model.AidsInputs;
import fr.pacpilot.core.aids.model.AidsOutcome;
import fr.pacpilot.core.aids.port.ResolveAids;
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.core.quoting.model.LineItem;
import fr.pacpilot.core.quoting.model.ProductSnapshot;
import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.core.quoting.port.BuildQuote;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.server.dimensioning.api.ValidatedStudies;
import fr.pacpilot.server.quoting.application.port.out.QuoteRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the aids for a job and assembles the devis around them.
 *
 * <p><b>Aids first, then the devis.</b> {@code BuildQuote} takes {@code ResolvedAids} rather than
 * producing them, so the order is fixed by the port: a devis is assembled around a barème that has
 * already been resolved for its own date, never patched with aids afterwards.
 *
 * <p>Which means that while no barème is published (ADR-0017), <b>no devis can be issued</b>. That is
 * not a gap in this class: {@code ResolvedAids} requires an {@code AidRulePackVersion} — even
 * {@code none(...)} takes one — because a devis that cannot name the barème it was priced against is
 * not reproducible, and reproducibility is the only reason the document has legal weight. A sentinel
 * version would be the tempting fix and would poison every devis that carried it.
 */
@Service
public class IssueDevis {

    private final ResolveAids aids;
    private final BuildQuote assembler;
    private final QuoteRepository quotes;
    private final ValidatedStudies studies;

    IssueDevis(
            ResolveAids aids, BuildQuote assembler, QuoteRepository quotes, ValidatedStudies studies) {
        this.aids = aids;
        this.assembler = assembler;
        this.quotes = quotes;
        this.studies = studies;
    }

    /** Either a devis, or the reason no devis could be priced. Never both, never neither. */
    public sealed interface Outcome {
        record Issued(Quote devis) implements Outcome {}

        /** No published barème covers the devis date, so nothing can be priced (ADR-0017). */
        record NoBaremePublished(EffectiveDate effectiveDate) implements Outcome {}
    }

    @Transactional
    public Outcome issue(
            UUID studyId,
            AidsInputs aidsInputs,
            ProductSnapshot product,
            List<LineItem> lines,
            EffectiveDate effectiveDate) {

        ValidatedDimensioning study =
                studies.findValidated(studyId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "study "
                                                        + studyId
                                                        + " is missing or unsigned; a devis is only ever"
                                                        + " built on a validated study"));

        AidsOutcome resolved = aids.resolve(aidsInputs, effectiveDate);
        if (!(resolved instanceof AidsOutcome.Resolved priced)) {
            return new Outcome.NoBaremePublished(effectiveDate);
        }

        Quote devis =
                assembler.build(
                        study, product, lines, priced.getResolution().getAids(), effectiveDate);
        return new Outcome.Issued(quotes.save(devis));
    }
}
