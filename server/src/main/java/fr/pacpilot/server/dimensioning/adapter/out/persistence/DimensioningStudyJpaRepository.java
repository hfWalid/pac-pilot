package fr.pacpilot.server.dimensioning.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code dimensioning_study}. Package-private. */
interface DimensioningStudyJpaRepository extends JpaRepository<DimensioningStudyEntity, UUID> {}
