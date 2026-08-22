package fr.pacpilot.server.dimensioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.core.dimensioning.model.Assumption;
import fr.pacpilot.core.dimensioning.model.AssumptionsLog;
import fr.pacpilot.core.dimensioning.model.ComputedDimensioning;
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod;
import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome;
import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.dimensioning.model.HeatLoadResult;
import fr.pacpilot.core.dimensioning.model.InputsSnapshot;
import fr.pacpilot.core.dimensioning.model.InsulationLevel;
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.core.dimensioning.model.VentilationType;
import fr.pacpilot.core.shared.CeilingHeightM;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.DimensioningId;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
import fr.pacpilot.core.shared.InstallerId;
import fr.pacpilot.core.shared.InstantUtc;
import fr.pacpilot.core.shared.PowerBand;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.SiteId;
import fr.pacpilot.core.shared.SurfaceM2;
import fr.pacpilot.core.shared.TemperatureC;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
import fr.pacpilot.server.dossier.application.port.out.ClientRepository;
import fr.pacpilot.server.dossier.application.port.out.SiteRepository;
import fr.pacpilot.server.dossier.domain.Client;
import fr.pacpilot.server.dossier.domain.DwellingObservations;
import fr.pacpilot.server.dossier.domain.Site;
import fr.pacpilot.server.dossier.domain.SiteAddress;
import fr.pacpilot.server.identity.application.port.out.InstallerRepository;
import fr.pacpilot.server.identity.domain.Installer;
import fr.pacpilot.server.identity.domain.Siret;
import java.time.Instant;
import java.util.List;
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
 * The aggregate whose shape the persistence layer most wants to flatten.
 *
 * <p>{@code Dimensioning} is a sealed pair with no {@code copy()} — that is the legal shield
 * ({@code CLAUDE.md} §4.5) — and a table has one row shape with a nullable pair. These tests pin the
 * translation in both directions, and pin that the database refuses the states the domain cannot
 * express.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class DimensioningPersistenceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private DimensioningRepository studies;
    @Autowired private SiteRepository sites;
    @Autowired private ClientRepository clients;
    @Autowired private InstallerRepository installers;
    @Autowired private DataSource dataSource;

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final AtomicLong NEXT_SIRET = new AtomicLong(70_000_000_000_001L);

    private UUID anInstaller() {
        return installers
                .save(
                        new Installer(
                                UUID.randomUUID(),
                                "Chauffage Berthier",
                                new Siret(String.format("%014d", NEXT_SIRET.getAndIncrement())),
                                Optional.empty(),
                                RECORDED_AT,
                                RECORDED_AT))
                .id();
    }

    /** A study references a site, and the site references a client and an installer (ADR-0016). */
    private UUID aSite() {
        UUID installerId = anInstaller();
        Client client =
                clients.save(
                        new Client(
                                UUID.randomUUID(),
                                installerId,
                                "Camille",
                                "Berthier",
                                Optional.empty(),
                                Optional.empty(),
                                RECORDED_AT,
                                RECORDED_AT,
                                Optional.empty()));
        return sites.save(
                        new Site(
                                UUID.randomUUID(),
                                client.id(),
                                new SiteAddress(
                                        "12 rue des Lilas",
                                        "69003",
                                        "Lyon",
                                        "69",
                                        Optional.empty(),
                                        Optional.empty()),
                                new DwellingObservations(
                                        12_000, 250, "BEFORE_1975", "PARTIAL", "VMC_SIMPLE_FLUX",
                                        "RADIATOR_HIGH_TEMPERATURE", 9),
                                RECORDED_AT,
                                RECORDED_AT,
                                Optional.empty()))
                .id();
    }

    private static final AssumptionsLog LOG =
            new AssumptionsLog(
                    List.of(
                            new Assumption("U-value from construction period", "SOURCE_TBD (provisional)"),
                            new Assumption("Envelope area inferred from floor area", "SOURCE_TBD (provisional)"),
                            new Assumption("Air-change rate from ventilation type", "SOURCE_TBD (provisional)")));

    private ComputedDimensioning aComputedStudy(UUID siteId, TemperatureC flowTemperature) {
        return Dimensioning.Companion.computed(
                new DimensioningId(UUID.randomUUID().toString()),
                new SiteId(siteId.toString()),
                new InputsSnapshot(
                        new SurfaceM2(12_000),
                        new CeilingHeightM(250),
                        ConstructionPeriod.BEFORE_1975,
                        InsulationLevel.PARTIAL,
                        VentilationType.VMC_SIMPLE_FLUX,
                        EmitterType.RADIATOR_HIGH_TEMPERATURE,
                        ClimateZone.H1,
                        new TemperatureC(-70),
                        new TemperatureC(190),
                        new ElectricalSupplyKva(9)),
                new DimensioningOutcome.Computed(
                        new HeatLoadResult(
                                new PowerKw(19_032),
                                new PowerBand(new PowerKw(17_129), new PowerKw(22_838)),
                                flowTemperature,
                                LOG)));
    }

    @Test
    void aSignedStudyComesBackAsTheValidatedCaseWithItsSignerAndInstant() {
        InstallerId signer = new InstallerId(anInstaller().toString());
        InstantUtc signedAt = new InstantUtc(Instant.parse("2026-08-22T14:30:00Z").toEpochMilli());
        ValidatedDimensioning saved =
                aComputedStudy(aSite(), new TemperatureC(500)).validate(signer, signedAt);

        studies.save(saved);
        Dimensioning loaded = studies.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded).isInstanceOf(ValidatedDimensioning.class);
        var validation = ((ValidatedDimensioning) loaded).getValidation();
        assertThat(validation.getValidatedBy()).isEqualTo(signer);
        assertThat(validation.getValidatedAt()).isEqualTo(signedAt);
    }

    @Test
    void anUnsignedStudyComesBackAsTheComputedCaseWithNoSignatureToRead() {
        // The compiler is the assertion here: ComputedDimensioning has no validation to read at all,
        // so a caller cannot mistake an unsigned study for a signed one.
        ComputedDimensioning saved = aComputedStudy(aSite(), new TemperatureC(500));

        studies.save(saved);
        Dimensioning loaded = studies.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded).isInstanceOf(ComputedDimensioning.class);
        assertThat(loaded).isNotInstanceOf(ValidatedDimensioning.class);
    }

    @Test
    void everyInputAndEveryFigureSurvivesTheRoundTripExactly() {
        // The reproducibility anchor. A study recomputed from these inputs years later must land on
        // the same watt, so nothing here may be stored as a float or rounded on the way through.
        ComputedDimensioning saved = aComputedStudy(aSite(), new TemperatureC(500));

        studies.save(saved);
        Dimensioning loaded = studies.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getInputs()).isEqualTo(saved.getInputs());
        assertThat(loaded.getResult()).isEqualTo(saved.getResult());
        assertThat(loaded.getResult().getHeatLoad().render()).isEqualTo("19.032");
        assertThat(loaded.getSiteId()).isEqualTo(saved.getSiteId());
    }

    @Test
    void theAssumptionsLogKeepsItsOrderAndItsSources() {
        // What an auditor reads down. Order is meaning: it is the order the method made its
        // assumptions in, so a set-shaped round trip would lose the reasoning while keeping the data.
        ComputedDimensioning saved = aComputedStudy(aSite(), new TemperatureC(500));

        studies.save(saved);
        Dimensioning loaded = studies.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getResult().getAssumptions().getEntries())
                .containsExactlyElementsOf(LOG.getEntries());
        assertThat(loaded.getResult().getConfidence().name()).isEqualTo("INDICATIVE");
    }

    @Test
    void aWithheldFlowTemperatureStaysWithheldRatherThanBecomingZero() {
        // Null means the method declined to advise; the study still stands. Zero would be advice.
        ComputedDimensioning saved = aComputedStudy(aSite(), null);

        studies.save(saved);
        Dimensioning loaded = studies.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getResult().getRecommendedFlowTemperature()).isNull();
    }

    @Test
    void theDatabaseRefusesHalfASignature() throws Exception {
        // A study signed by nobody at a known instant, or by somebody at no instant, is corrupt
        // rather than unusual — the mapper would have no case to map it to. Enforced by constraint,
        // not only by the code path that happens to write it today.
        ComputedDimensioning saved = aComputedStudy(aSite(), new TemperatureC(500));
        studies.save(saved);
        UUID id = UUID.fromString(saved.getId().getValue());

        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "update dimensioning_study set validated_at = now() where id = ?")) {
            statement.setObject(1, id);
            assertThatThrownBy(statement::executeUpdate)
                    .hasMessageContaining("dimensioning_study_validation_is_whole");
        }
    }

    @Test
    void savingTheSameStudyTwicePreservesWhenItWasFirstRecorded() {
        // Re-saving is the shape M8's idempotent ingestion needs. created_at is a record-keeping
        // fact: a replay must not rewrite when the study first arrived.
        UUID siteId = aSite();
        ComputedDimensioning first = aComputedStudy(siteId, new TemperatureC(500));
        studies.save(first);
        Instant firstRecordedAt = createdAtOf(UUID.fromString(first.getId().getValue()));

        studies.save(first.validate(new InstallerId(anInstaller().toString()), new InstantUtc(1_000)));

        assertThat(createdAtOf(UUID.fromString(first.getId().getValue()))).isEqualTo(firstRecordedAt);
        assertThat(studies.findById(UUID.fromString(first.getId().getValue())).orElseThrow())
                .isInstanceOf(ValidatedDimensioning.class);
    }

    private Instant createdAtOf(UUID id) throws RuntimeException {
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement("select created_at from dimensioning_study where id = ?")) {
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getTimestamp("created_at").toInstant();
            }
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }
}
