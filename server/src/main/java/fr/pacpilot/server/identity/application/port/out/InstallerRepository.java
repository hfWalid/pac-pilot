package fr.pacpilot.server.identity.application.port.out;

import fr.pacpilot.server.identity.domain.Installer;
import java.util.Optional;
import java.util.UUID;

/** Driven port — where installer accounts are kept. Upsert, as everywhere else here. */
public interface InstallerRepository {

    Installer save(Installer installer);

    Optional<Installer> findById(UUID id);
}
