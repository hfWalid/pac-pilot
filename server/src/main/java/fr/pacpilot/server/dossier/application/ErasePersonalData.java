package fr.pacpilot.server.dossier.application;

import fr.pacpilot.server.dossier.application.port.out.ClientRepository;
import fr.pacpilot.server.dossier.application.port.out.SiteRepository;
import fr.pacpilot.server.dossier.domain.Client;
import fr.pacpilot.server.dossier.domain.Site;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The right to erasure, as ADR-0014 resolved it.
 *
 * <p><b>Reproducibility and erasure pull in opposite directions, and this class is where that is
 * settled rather than argued.</b> A validated study and the devis built on it are evidence — what an
 * artisan shows an auditor, an insurer, or a client disputing a figure. Hard-deleting them on
 * request would destroy the artisan's own défense, and retention for a legal obligation is a
 * recognised limit on the right to erasure.
 *
 * <p>So erasure <b>severs the identity and keeps the arithmetic</b>: names replaced, contact details
 * and geocode removed outright, address severed — while the dwelling characteristics, the study and
 * the devis survive, no longer reachable from a named person. A devis that reproduces its figures
 * without naming anyone is still an audit artefact.
 *
 * <p>It reaches every site the client owns, not only the client row. A delete that misses a
 * denormalised copy has not deleted anything.
 */
@Service
public class ErasePersonalData {

    private final ClientRepository clients;
    private final SiteRepository sites;
    private final Clock clock;

    ErasePersonalData(ClientRepository clients, SiteRepository sites, Clock clock) {
        this.clients = clients;
        this.sites = sites;
        this.clock = clock;
    }

    /**
     * @return the anonymised client, so a caller can confirm what happened rather than assume it.
     * @throws IllegalArgumentException when there is no such client. Erasing something absent is a
     *     caller error, not a silent no-op — a request that quietly did nothing would be reported to
     *     the data subject as completed.
     */
    @Transactional
    public Client erase(UUID clientId) {
        Client client =
                clients.findById(clientId)
                        .orElseThrow(() -> new IllegalArgumentException("no client " + clientId + " to erase"));

        Instant at = clock.instant();

        // Sites first. If this failed after the client row was severed, the address would survive
        // with nothing left pointing at it as personal data — the worst of both outcomes. One
        // transaction, and the order still reflects the intent.
        List<Site> owned = sites.findByClientId(clientId);
        owned.forEach(site -> sites.save(site.anonymised(at)));

        return clients.save(client.anonymised(at));
    }
}
