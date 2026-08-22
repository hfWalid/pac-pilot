package fr.pacpilot.server.quoting.application.port.out;

import fr.pacpilot.core.quoting.model.Quote;
import java.util.Optional;
import java.util.UUID;

/** Driven port — where devis are kept. Takes and returns the {@code :core} aggregate. */
public interface QuoteRepository {

    Quote save(Quote quote);

    /**
     * @throws CorruptQuoteException when the stored row cannot be rebuilt into a valid devis — its
     *     study is missing or unsigned, or its status is unreachable. A corrupt devis fails loudly
     *     rather than loading in a degraded shape, and an empty {@code Optional} would report it as
     *     "no devis here", which is a quieter untruth.
     */
    Optional<Quote> findById(UUID id);
}
