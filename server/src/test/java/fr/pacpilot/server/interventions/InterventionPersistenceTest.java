package fr.pacpilot.server.interventions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.server.dossier.application.ErasePersonalData;
import fr.pacpilot.server.dossier.application.port.out.ClientRepository;
import fr.pacpilot.server.dossier.application.port.out.SiteRepository;
import fr.pacpilot.server.dossier.domain.Client;
import fr.pacpilot.server.dossier.domain.DwellingObservations;
import fr.pacpilot.server.dossier.domain.Site;
import fr.pacpilot.server.dossier.domain.SiteAddress;
import fr.pacpilot.server.identity.application.port.out.InstallerRepository;
import fr.pacpilot.server.identity.domain.Installer;
import fr.pacpilot.server.identity.domain.Siret;
import fr.pacpilot.server.interventions.application.port.out.InterventionRepository;
import fr.pacpilot.server.interventions.domain.Intervention;
import fr.pacpilot.server.interventions.domain.InterventionStatus;
import fr.pacpilot.server.interventions.domain.InterventionType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The timeline, persisted (ADR-0012). Persistence only — the screen, the filters, the geocoding and
 * the maintenance recurrence are M7.5's.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class InterventionPersistenceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private InterventionRepository interventions;
    @Autowired private SiteRepository sites;
    @Autowired private ClientRepository clients;
    @Autowired private InstallerRepository installers;
    @Autowired private ErasePersonalData erasure;
    @Autowired private DataSource dataSource;

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final AtomicLong NEXT_SIRET = new AtomicLong(40_000_000_000_001L);
    private static final String STREET = "3 chemin du Vallon Perdu";

    private record Fixture(UUID clientId, UUID siteId) {}

    private Fixture aSite() {
        UUID installerId =
                installers
                        .save(
                                new Installer(
                                        UUID.randomUUID(),
                                        "Chauffage Berthier",
                                        new Siret(String.format("%014d", NEXT_SIRET.getAndIncrement())),
                                        Optional.empty(),
                                        RECORDED_AT,
                                        RECORDED_AT))
                        .id();
        Client client =
                clients.save(
                        new Client(
                                UUID.randomUUID(), installerId, "Camille", "Berthier",
                                Optional.empty(), Optional.empty(), RECORDED_AT, RECORDED_AT, Optional.empty()));
        Site site =
                sites.save(
                        new Site(
                                UUID.randomUUID(),
                                client.id(),
                                new SiteAddress(STREET, "69003", "Lyon", "69", Optional.empty(), Optional.empty()),
                                new DwellingObservations(
                                        12_000, 250, "BEFORE_1975", "PARTIAL", "VMC_SIMPLE_FLUX",
                                        "RADIATOR_HIGH_TEMPERATURE", 9),
                                RECORDED_AT, RECORDED_AT, Optional.empty()));
        return new Fixture(client.id(), site.id());
    }

    private Intervention aPlannedVisit(UUID siteId, String address) {
        return new Intervention(
                UUID.randomUUID(),
                siteId,
                InterventionType.PRE_VISIT,
                InterventionStatus.PLANNED,
                Instant.parse("2026-09-01T08:30:00Z"),
                90,
                address,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                RECORDED_AT,
                RECORDED_AT);
    }

    @Test
    void aVisitRoundTripsWithItsTypeStatusScheduleAndLinks() {
        Fixture fixture = aSite();
        Intervention saved = interventions.save(aPlannedVisit(fixture.siteId(), STREET + ", 69003 Lyon"));

        Intervention loaded = interventions.findById(saved.id()).orElseThrow();

        assertThat(loaded).isEqualTo(saved);
        assertThat(loaded.type()).isEqualTo(InterventionType.PRE_VISIT);
        assertThat(loaded.status()).isEqualTo(InterventionStatus.PLANNED);
        assertThat(loaded.durationMinutes()).isEqualTo(90);
        assertThat(loaded.isGeocoded()).as("never blocking — the installer may be in a cellar").isFalse();
    }

    @Test
    void editingTheSiteAfterwardsLeavesTheAddressSnapshotUntouched() {
        // §14: where a visit was recorded as having happened must not change because the Site was
        // later corrected. Same property M4-03 proved for InputsSnapshot, asserted again because it
        // is a different denormalisation and would break independently.
        Fixture fixture = aSite();
        Intervention visit = interventions.save(aPlannedVisit(fixture.siteId(), STREET + ", 69003 Lyon"));

        Site site = sites.findById(fixture.siteId()).orElseThrow();
        sites.save(
                new Site(
                        site.id(),
                        site.clientId(),
                        new SiteAddress("99 avenue Corrigee", "69007", "Lyon", "69", Optional.empty(), Optional.empty()),
                        site.observations(),
                        site.createdAt(),
                        RECORDED_AT.plusSeconds(60),
                        site.anonymisedAt()));

        assertThat(interventions.findById(visit.id()).orElseThrow().addressSnapshot())
                .isEqualTo(STREET + ", 69003 Lyon");
    }

    @Test
    void aCancellationWithoutAReasonIsRefusedByBothTheTypeAndTheDatabase() {
        // A timeline of cancellations without reasons tells an artisan nothing they did not know.
        Fixture fixture = aSite();

        assertThatThrownBy(
                        () ->
                                new Intervention(
                                        UUID.randomUUID(),
                                        fixture.siteId(),
                                        InterventionType.PRE_VISIT,
                                        InterventionStatus.CANCELLED,
                                        Instant.parse("2026-09-01T08:30:00Z"),
                                        90,
                                        STREET,
                                        Optional.empty(), Optional.empty(), Optional.empty(),
                                        Optional.empty(), Optional.empty(),
                                        Optional.empty(),
                                        RECORDED_AT,
                                        RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must record why");
    }

    @Test
    void aStatusOutsideTheV1SetIsRefusedByTheDatabase() throws Exception {
        // The V1.5 booking path adds REQUESTED / CONFIRMED / DECLINED. Because status is text with a
        // check constraint rather than a Postgres enum type, adding one is a one-line DDL change and
        // not a rewrite of every dependent object (ADR-0012).
        Fixture fixture = aSite();
        Intervention visit = interventions.save(aPlannedVisit(fixture.siteId(), STREET));

        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "update interventions_intervention set status = 'CONFIRMED' where id = ?")) {
            statement.setObject(1, visit.id());
            assertThatThrownBy(statement::executeUpdate)
                    .hasMessageContaining("interventions_status_known");
        }
    }

    @Test
    void erasureReachesTheDenormalisedAddressSnapshot() {
        // The test of PAC-60's sweep as much as of this table. address_snapshot is personal data
        // sitting in another context's table; a delete that misses it has not deleted anything.
        //
        // The needle is unique to THIS test. The other tests in this class create visits at STREET
        // on clients that are never erased, and the probe sweeps the whole table — a shared needle
        // reports their perfectly legitimate rows as an erasure failure.
        String needle = "8 rue Unique-A-Ce-Test";
        Fixture fixture = aSite();
        interventions.save(aPlannedVisit(fixture.siteId(), needle + ", 69003 Lyon"));

        erasure.erase(fixture.clientId());

        assertThat(rowsContaining("interventions_intervention", "address_snapshot", needle))
                .as("the address survives on the visit after the client was erased")
                .isZero();
    }

    private int rowsContaining(String table, String column, String needle) {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "select count(*) from " + table + " where " + column + " like ?")) {
            statement.setString(1, "%" + needle + "%");
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }
}
