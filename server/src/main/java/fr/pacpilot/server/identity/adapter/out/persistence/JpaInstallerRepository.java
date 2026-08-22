package fr.pacpilot.server.identity.adapter.out.persistence;

import fr.pacpilot.server.identity.application.port.out.InstallerRepository;
import fr.pacpilot.server.identity.domain.Installer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Satisfies {@link InstallerRepository} with JPA. */
@Repository
class JpaInstallerRepository implements InstallerRepository {

    private final InstallerJpaRepository rows;

    JpaInstallerRepository(InstallerJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional
    public Installer save(Installer installer) {
        return IdentityMapper.toDomain(rows.save(IdentityMapper.toEntity(installer)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Installer> findById(UUID id) {
        return rows.findById(id).map(IdentityMapper::toDomain);
    }
}
