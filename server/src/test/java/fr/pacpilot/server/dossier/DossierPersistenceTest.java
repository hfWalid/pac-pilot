package fr.pacpilot.server.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.server.dossier.application.port.out.ClientRepository;
import fr.pacpilot.server.dossier.application.port.out.SiteRepository;
import fr.pacpilot.server.dossier.domain.Client;
import fr.pacpilot.server.dossier.domain.DwellingObservations;
import fr.pacpilot.server.dossier.domain.Site;
import fr.pacpilot.server.dossier.domain.SiteAddress;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The Dossier context against a real PostgreSQL. An in-memory substitute would prove nothing here:
 * the check constraints, the {@code uuid} primary keys and the foreign key are the schema decisions
 * under test, and they are dialect-specific.
 *
 * <p>{@code disabledWithoutDocker} keeps {@code ./gradlew build} runnable without a Docker daemon,
 * following {@code FlywayBaselineMigrationTest} from M0-05. <b>These tests skip in that case rather
 * than failing</b> — a green summary on a machine without Docker does not mean this ran. Check the
 * XML for {@code skipped="0"}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        // ADR-0015 gives this property no default, so every full-context test states it. A
        // persistence test declaring a dimensioning method reads oddly and is the honest cost of a
        // configuration that refuses to choose one for you.
        properties = "pacpilot.dimensioning.method=indicative-provisional")
class DossierPersistenceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private ClientRepository clients;
    @Autowired private SiteRepository sites;

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");

    private Client aClient() {
        return new Client(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Camille",
                "Berthier",
                Optional.of("camille.berthier@example.fr"),
                Optional.of("+33600000000"),
                RECORDED_AT,
                RECORDED_AT,
                Optional.empty());
    }

    private static DwellingObservations observations(int surfaceCentiSquareMetres) {
        return new DwellingObservations(
                surfaceCentiSquareMetres,
                250,
                "BEFORE_1975",
                "PARTIAL",
                "VMC_SIMPLE_FLUX",
                "RADIATOR_HIGH_TEMPERATURE",
                9);
    }

    private Site aSiteOf(Client client, int surfaceCentiSquareMetres) {
        return new Site(
                UUID.randomUUID(),
                client.id(),
                new SiteAddress(
                        "12 rue des Tanneurs",
                        "69001",
                        "Lyon",
                        "69",
                        Optional.of(new BigDecimal("45.767300")),
                        Optional.of(new BigDecimal("4.833400"))),
                observations(surfaceCentiSquareMetres),
                RECORDED_AT,
                RECORDED_AT,
                Optional.empty());
    }

    @Test
    void aClientAndTheirSiteSurviveARoundTrip() {
        Client client = clients.save(aClient());
        Site site = sites.save(aSiteOf(client, 12_000));

        assertThat(clients.findById(client.id())).contains(client);
        assertThat(sites.findById(site.id())).contains(site);
        assertThat(sites.findByClientId(client.id())).containsExactly(site);
    }

    @Test
    void theDatabaseNeverMintsAnIdentifier() {
        // Ids are generated on the installer's device, offline (CLAUDE.md §4.3). The id that comes
        // back must be the one that went in — a sequence or @GeneratedValue here would break every
        // reference the device already holds.
        UUID mintedOnTheDevice = UUID.randomUUID();
        Client client = aClient();
        Client saved = clients.save(
                new Client(
                        mintedOnTheDevice,
                        client.installerId(),
                        client.firstName(),
                        client.lastName(),
                        client.email(),
                        client.phone(),
                        client.createdAt(),
                        client.updatedAt(),
                        client.anonymisedAt()));

        assertThat(saved.id()).isEqualTo(mintedOnTheDevice);
    }

    @Test
    void savingTheSameClientTwiceUpsertsRatherThanDuplicating() {
        // An outbox replay presents the same aggregate again (CLAUDE.md §8). M8 builds idempotent
        // ingestion on this; the key design has to allow it from the first migration.
        Client first = clients.save(aClient());
        Client corrected =
                new Client(
                        first.id(),
                        first.installerId(),
                        first.firstName(),
                        "Berthier-Roux",
                        first.email(),
                        first.phone(),
                        first.createdAt(),
                        RECORDED_AT.plusSeconds(60),
                        first.anonymisedAt());

        clients.save(corrected);

        assertThat(clients.findById(first.id())).contains(corrected);
    }

    @Test
    void editingASiteDoesNotReachBackIntoAStudyAlreadyTakenFromIt() {
        // The audit chain, tested rather than assumed. A dimensioning copies the observations it was
        // computed from; correcting the site afterwards is a legitimate edit and must leave the
        // earlier study describing what was actually observed that day.
        Client client = clients.save(aClient());
        Site asVisited = sites.save(aSiteOf(client, 12_000));
        DwellingObservations copiedIntoTheStudy = asVisited.observations();

        sites.save(asVisited.observing(observations(15_000), RECORDED_AT.plusSeconds(86_400)));

        assertThat(sites.findById(asVisited.id()).orElseThrow().observations().surfaceCentiSquareMetres())
                .as("the site itself is corrected")
                .isEqualTo(15_000);
        assertThat(copiedIntoTheStudy.surfaceCentiSquareMetres())
                .as("what the study was computed from is untouched")
                .isEqualTo(12_000);
    }

    @Test
    void anUngeocodedSiteIsSavedRatherThanRefused() {
        // Geocoding is opportunistic and never blocks (CLAUDE.md §14). The installer is in a cellar.
        Client client = clients.save(aClient());
        Site withoutPosition =
                new Site(
                        UUID.randomUUID(),
                        client.id(),
                        new SiteAddress(
                                "3 impasse du Puits", "69005", "Lyon", "69", Optional.empty(), Optional.empty()),
                        observations(9_000),
                        RECORDED_AT,
                        RECORDED_AT,
                        Optional.empty());

        Site saved = sites.save(withoutPosition);

        assertThat(saved.address().isGeocoded()).isFalse();
        assertThat(sites.findById(saved.id())).contains(saved);
    }

    @Test
    void erasureSeversContactDetailsWhileTheRecordSurvives() {
        // ADR-0014: email and phone are hard-deleted, the row survives de-linked, and anonymised_at
        // records that it happened. The column exists from the first migration for this reason.
        Client client = clients.save(aClient());
        Instant erasedAt = RECORDED_AT.plusSeconds(3_600);

        Client erased =
                clients.save(
                        new Client(
                                client.id(),
                                client.installerId(),
                                "—",
                                "—",
                                Optional.empty(),
                                Optional.empty(),
                                client.createdAt(),
                                erasedAt,
                                Optional.of(erasedAt)));

        Client reloaded = clients.findById(client.id()).orElseThrow();
        assertThat(reloaded.email()).isEmpty();
        assertThat(reloaded.phone()).isEmpty();
        assertThat(reloaded.isAnonymised()).isTrue();
    }
}
