package fr.pacpilot.server.dimensioning.adapter.out.persistence;

import fr.pacpilot.server.dimensioning.application.port.out.VerificationRecord;
import fr.pacpilot.server.dimensioning.application.port.out.VerificationRecordRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data's view of {@code dimensioning_verification}. Package-private. */
interface VerificationJpaRepository extends JpaRepository<VerificationEntity, UUID> {
    List<VerificationEntity> findByStudyIdOrderByVerifiedAtDesc(UUID studyId);
}

/** Satisfies {@link VerificationRecordRepository} with JPA. */
@Repository
class JpaVerificationRecordRepository implements VerificationRecordRepository {

    private final VerificationJpaRepository rows;

    JpaVerificationRecordRepository(VerificationJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional
    public VerificationRecord save(VerificationRecord record) {
        return toDomain(
                rows.save(
                        new VerificationEntity(
                                record.id(),
                                record.studyId(),
                                record.verifiedAt(),
                                record.outcome(),
                                record.differences().orElse(null),
                                record.reason().orElse(null))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VerificationRecord> findByStudyId(UUID studyId) {
        return rows.findByStudyIdOrderByVerifiedAtDesc(studyId).stream()
                .map(JpaVerificationRecordRepository::toDomain)
                .toList();
    }

    private static VerificationRecord toDomain(VerificationEntity entity) {
        return new VerificationRecord(
                entity.getId(),
                entity.getStudyId(),
                entity.getVerifiedAt(),
                entity.getOutcome(),
                Optional.ofNullable(entity.getDifferences()),
                Optional.ofNullable(entity.getReason()));
    }
}
