package fr.pacpilot.server.interventions.adapter.out.persistence;

import fr.pacpilot.server.interventions.application.port.out.InterventionRepository;
import fr.pacpilot.server.interventions.domain.Intervention;
import fr.pacpilot.server.interventions.domain.InterventionStatus;
import fr.pacpilot.server.interventions.domain.InterventionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data's view of {@code interventions_intervention}. Package-private. */
interface InterventionJpaRepository extends JpaRepository<InterventionEntity, UUID> {
    List<InterventionEntity> findBySiteIdOrderByScheduledAtDesc(UUID siteId);
}

/** Satisfies {@link InterventionRepository} with JPA. */
@Repository
class JpaInterventionRepository implements InterventionRepository {

    private final InterventionJpaRepository rows;

    JpaInterventionRepository(InterventionJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional
    public Intervention save(Intervention intervention) {
        return toDomain(rows.save(toEntity(intervention)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Intervention> findById(UUID id) {
        return rows.findById(id).map(JpaInterventionRepository::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Intervention> findBySiteId(UUID siteId) {
        return rows.findBySiteIdOrderByScheduledAtDesc(siteId).stream()
                .map(JpaInterventionRepository::toDomain)
                .toList();
    }

    private static InterventionEntity toEntity(Intervention intervention) {
        return new InterventionEntity(
                intervention.id(),
                intervention.siteId(),
                intervention.type().name(),
                intervention.status().name(),
                intervention.scheduledAt(),
                intervention.durationMinutes(),
                intervention.addressSnapshot(),
                intervention.latitude().orElse(null),
                intervention.longitude().orElse(null),
                intervention.outcomeNotes().orElse(null),
                intervention.dimensioningId().orElse(null),
                intervention.quoteId().orElse(null),
                intervention.cancellationReason().orElse(null),
                intervention.createdAt(),
                intervention.updatedAt());
    }

    private static Intervention toDomain(InterventionEntity entity) {
        return new Intervention(
                entity.getId(),
                entity.getSiteId(),
                InterventionType.valueOf(entity.getType()),
                InterventionStatus.valueOf(entity.getStatus()),
                entity.getScheduledAt(),
                entity.getDurationMinutes(),
                entity.getAddressSnapshot(),
                Optional.ofNullable(entity.getLatitude()),
                Optional.ofNullable(entity.getLongitude()),
                Optional.ofNullable(entity.getOutcomeNotes()),
                Optional.ofNullable(entity.getDimensioningId()),
                Optional.ofNullable(entity.getQuoteId()),
                Optional.ofNullable(entity.getCancellationReason()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
