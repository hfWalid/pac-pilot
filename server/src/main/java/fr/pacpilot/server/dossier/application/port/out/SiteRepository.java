package fr.pacpilot.server.dossier.application.port.out;

import fr.pacpilot.server.dossier.domain.Site;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Driven port — where sites are kept. Upsert semantics, for the reason given on ClientRepository. */
public interface SiteRepository {

    Site save(Site site);

    Optional<Site> findById(UUID id);

    List<Site> findByClientId(UUID clientId);
}
