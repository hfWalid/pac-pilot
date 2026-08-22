package fr.pacpilot.server.dossier.adapter.out.persistence;

import fr.pacpilot.server.dossier.application.port.out.ClientRepository;
import fr.pacpilot.server.dossier.domain.Client;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Satisfies {@link ClientRepository} with JPA.
 *
 * <p>The transaction boundary sits here rather than in the use case, for now, because M4 has no
 * operation spanning two aggregates. When one appears — M4-08's create-dossier-then-dimension flow
 * is the first candidate — the boundary moves outward to the use case and this annotation goes,
 * because a transaction per repository call is how a half-written flow gets committed.
 */
@Repository
class JpaClientRepository implements ClientRepository {

    private final ClientJpaRepository rows;

    JpaClientRepository(ClientJpaRepository rows) {
        this.rows = rows;
    }

    @Override
    @Transactional
    public Client save(Client client) {
        // Upsert, not insert: the id came from the device, and an outbox replay may present the
        // same client twice (CLAUDE.md §8). `save` on a detached entity with an assigned id is a
        // merge, which is exactly the semantics wanted.
        return DossierMapper.toDomain(rows.save(DossierMapper.toEntity(client)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Client> findById(UUID id) {
        return rows.findById(id).map(DossierMapper::toDomain);
    }
}
