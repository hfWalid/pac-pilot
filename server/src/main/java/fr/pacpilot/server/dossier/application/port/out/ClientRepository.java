package fr.pacpilot.server.dossier.application.port.out;

import fr.pacpilot.server.dossier.domain.Client;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port — where clients are kept.
 *
 * <p>Owned by the application layer and implemented by an adapter, so the use cases below it never
 * learn what a {@code @Entity} is (ARCHITECTURE #5, enforced by {@code BoundedContextRulesTest}).
 *
 * <p>{@link #save} is an upsert rather than an insert. Ids arrive client-generated from a device
 * that queues changes in an outbox and replays them, so the same client can legitimately be
 * presented twice; M8 builds idempotent ingestion on exactly this shape.
 */
public interface ClientRepository {

    Client save(Client client);

    Optional<Client> findById(UUID id);
}
