package fr.pacpilot.server.quoting.adapter.out.persistence;

import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.server.dimensioning.api.ValidatedStudies;
import fr.pacpilot.server.quoting.application.port.out.CorruptQuoteException;
import fr.pacpilot.server.quoting.application.port.out.QuoteRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satisfies {@link QuoteRepository} with JPA.
 *
 * <p>Reaches the Dimensioning context through {@link ValidatedStudies}, its published surface —
 * never through its repository or its tables. That is what keeps the two extractable, and it is
 * enforced by {@code BoundedContextRulesTest} rather than by convention.
 */
@Repository
class JpaQuoteRepository implements QuoteRepository {

    private final QuoteJpaRepository rows;
    private final ValidatedStudies studies;
    private final Clock clock;

    JpaQuoteRepository(QuoteJpaRepository rows, ValidatedStudies studies, Clock clock) {
        this.rows = rows;
        this.studies = studies;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Quote save(Quote quote) {
        UUID id = UUID.fromString(quote.getId().getValue());
        Instant createdAt =
                rows.findById(id).map(QuoteEntity::getCreatedAt).orElseGet(clock::instant);
        UUID dimensioningId = UUID.fromString(quote.getDimensioning().getId().getValue());

        return load(rows.save(QuoteMapper.toEntity(quote, dimensioningId, createdAt)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Quote> findById(UUID id) {
        return rows.findById(id).map(this::load);
    }

    /**
     * Rebuilds the devis around its study.
     *
     * <p>A devis whose study is missing or unsigned throws {@link CorruptQuoteException}.
     * {@code ARCHITECTURE} #7 allows only {@code Validated → Quoted}, so such a row could not have
     * been written by any legitimate path; returning an empty {@code Optional} would report it as
     * "no devis here", which is a different and much quieter untruth than "this devis is corrupt".
     */
    private Quote load(QuoteEntity entity) {
        var study =
                studies.findValidated(entity.getDimensioningId())
                        .orElseThrow(
                                () ->
                                        new CorruptQuoteException(
                                                "devis "
                                                        + entity.getId()
                                                        + " references study "
                                                        + entity.getDimensioningId()
                                                        + ", which is missing or unsigned; a devis is only"
                                                        + " ever built on a validated study"));
        return QuoteMapper.toDomain(entity, study);
    }
}
