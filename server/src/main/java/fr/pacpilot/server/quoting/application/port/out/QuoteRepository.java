package fr.pacpilot.server.quoting.application.port.out;

import fr.pacpilot.core.aids.model.IncomeDecile;
import fr.pacpilot.core.quoting.model.Quote;
import java.util.Optional;
import java.util.UUID;

/** Driven port — where devis are kept. Takes and returns the {@code :core} aggregate. */
public interface QuoteRepository {

    /**
     * @param incomeDecile the band that produced this devis's aids, or empty when no aids were
     *     resolved — which is every devis today (ADR-0017). Stored because the verifier recomputes
     *     the aids from it (ADR-0014); empty means "no aids were resolved", never "decile unknown".
     *     <p>Passed alongside rather than carried on {@link Quote}: it is sensitive personal data
     *     that the core has no reason to hold on an aggregate the PWA also builds and caches.
     */
    Quote save(Quote quote, Optional<IncomeDecile> incomeDecile);

    /**
     * @throws CorruptQuoteException when the stored row cannot be rebuilt into a valid devis — its
     *     study is missing or unsigned, or its status is unreachable. A corrupt devis fails loudly
     *     rather than loading in a degraded shape, and an empty {@code Optional} would report it as
     *     "no devis here", which is a quieter untruth.
     */
    Optional<Quote> findById(UUID id);
}
