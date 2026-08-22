package fr.pacpilot.server.dimensioning.adapter.out.persistence;

import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satisfies {@link DimensioningRepository} with JPA.
 *
 * <p>The {@link Clock} is injected rather than reached for. {@code created_at} is a server-side
 * record-keeping fact, not a domain one — the core has no clock by design ({@code CLAUDE.md} §10),
 * and this is the boundary where wall-clock time is allowed to enter. Injecting it keeps that
 * boundary visible and the adapter testable.
 */
@Repository
class JpaDimensioningRepository implements DimensioningRepository {

    private final DimensioningStudyJpaRepository rows;
    private final Clock clock;

    JpaDimensioningRepository(DimensioningStudyJpaRepository rows, Clock clock) {
        this.rows = rows;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Dimensioning save(Dimensioning study) {
        Instant createdAt =
                rows.findById(UUID.fromString(study.getId().getValue()))
                        .map(DimensioningStudyEntity::getCreatedAt)
                        .orElseGet(clock::instant);

        return DimensioningMapper.toDomain(rows.save(DimensioningMapper.toEntity(study, createdAt)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dimensioning> findById(UUID id) {
        return rows.findById(id).map(DimensioningMapper::toDomain);
    }
}
