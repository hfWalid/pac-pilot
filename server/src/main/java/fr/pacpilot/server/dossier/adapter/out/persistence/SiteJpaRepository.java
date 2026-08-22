package fr.pacpilot.server.dossier.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code dossier_site}. Package-private: nothing outside may hold one. */
interface SiteJpaRepository extends JpaRepository<SiteEntity, UUID> {

    List<SiteEntity> findByClientIdOrderByCreatedAtAsc(UUID clientId);
}
