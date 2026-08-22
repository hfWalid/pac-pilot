package fr.pacpilot.server.dossier.adapter.out.persistence;

import fr.pacpilot.server.dossier.application.port.out.SiteRepository;
import fr.pacpilot.server.dossier.domain.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Satisfies {@link SiteRepository} with JPA. See {@link JpaClientRepository} on the boundary. */
@Repository
class JpaSiteRepository implements SiteRepository {

    private final SiteJpaRepository rows;

    JpaSiteRepository(SiteJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional
    public Site save(Site site) {
        return DossierMapper.toDomain(rows.save(DossierMapper.toEntity(site)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Site> findById(UUID id) {
        return rows.findById(id).map(DossierMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Site> findByClientId(UUID clientId) {
        return rows.findByClientIdOrderByCreatedAtAsc(clientId).stream().map(DossierMapper::toDomain).toList();
    }
}
