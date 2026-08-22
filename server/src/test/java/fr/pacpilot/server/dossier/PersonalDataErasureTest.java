package fr.pacpilot.server.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.core.dimensioning.model.Assumption;
import fr.pacpilot.core.dimensioning.model.AssumptionsLog;
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
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
import fr.pacpilot.core.shared.InstallerId;
import fr.pacpilot.core.shared.InstantUtc;
import fr.pacpilot.core.shared.PowerBand;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.SiteId;
import fr.pacpilot.core.shared.SurfaceM2;
import fr.pacpilot.core.shared.TemperatureC;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
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
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
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
 * Erasure, run the way PAC-60 asks an auditor to run it: erase, then search <b>every text column of
 * every table</b> for the name and the address. Not the columns we remember to check — all of them.
 *
 * <p>That sweep is the point. A hand-written list of places to look is exactly what misses the
 * denormalised copy, and a delete that misses one has not deleted anything.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class PersonalDataErasureTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private ErasePersonalData erasure;
    @Autowired private ClientRepository clients;
    @Autowired private SiteRepository sites;
    @Autowired private DimensioningRepository studies;
    @Autowired private InstallerRepository installers;
    @Autowired private DataSource dataSource;

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final AtomicLong NEXT_SIRET = new AtomicLong(80_000_000_000_001L);

    /** Strings chosen so a match anywhere is unambiguous rather than a coincidence. */
    private static final String SURNAME = "Zylberstein";
    private static final String STREET = "17 impasse des Glycines";
    private static final String EMAIL = "zylberstein.unique@example.invalid";
    private static final String PHONE = "0611223344";

    private record Fixture(UUID clientId, UUID siteId, UUID studyId) {}

    private Fixture aClientWithAValidatedStudy() {
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
                                UUID.randomUUID(),
                                installerId,
                                "Camille",
                                SURNAME,
                                Optional.of(EMAIL),
                                Optional.of(PHONE),
                                RECORDED_AT,
                                RECORDED_AT,
                                Optional.empty()));

        Site site =
                sites.save(
                        new Site(
                                UUID.randomUUID(),
                                client.id(),
                                new SiteAddress(
                                        STREET, "69003", "Lyon", "69",
                                        Optional.of(new java.math.BigDecimal("45.7500000")),
                                        Optional.of(new java.math.BigDecimal("4.8500000"))),
                                new DwellingObservations(
                                        12_000, 250, "BEFORE_1975", "PARTIAL", "VMC_SIMPLE_FLUX",
                                        "RADIATOR_HIGH_TEMPERATURE", 9),
                                RECORDED_AT,
                                RECORDED_AT,
                                Optional.empty()));

        ValidatedDimensioning study =
                Dimensioning.Companion.computed(
                                new DimensioningId(UUID.randomUUID().toString()),
                                new SiteId(site.id().toString()),
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
                                                new TemperatureC(500),
                                                new AssumptionsLog(
                                                        List.of(new Assumption("U-value", "SOURCE_TBD"))))),
                                new EffectiveDate(2026, 8, 22))
                        .validate(new InstallerId(installerId.toString()), new InstantUtc(1_000));
        studies.save(study);

        return new Fixture(client.id(), site.id(), UUID.fromString(study.getId().getValue()));
    }

    @Test
    void afterErasureNoTextColumnInAnyTableStillHoldsTheNameOrTheAddress() {
        Fixture fixture = aClientWithAValidatedStudy();

        erasure.erase(fixture.clientId());

        for (String needle : List.of(SURNAME, STREET, EMAIL, PHONE)) {
            assertThat(everyTextColumnContaining(needle))
                    .as("'%s' survives erasure in these columns", needle)
                    .isEmpty();
        }
    }

    @Test
    void theArithmeticSurvivesSoTheDevisStillReproduces() {
        // The other half of the trade. If erasure took the study with it, the artisan's own defence
        // against an auditor or an insurer would be gone — and retention for a legal obligation is a
        // recognised limit on the right to erasure (ADR-0014).
        Fixture fixture = aClientWithAValidatedStudy();

        erasure.erase(fixture.clientId());

        var study = studies.findById(fixture.studyId()).orElseThrow();
        assertThat(study.getResult().getHeatLoad().render()).isEqualTo("19.032");
        assertThat(study.getInputs().getSurface().render()).isEqualTo("120.00");
        assertThat(study).isInstanceOf(ValidatedDimensioning.class);
        assertThat(((ValidatedDimensioning) study).getValidation()).isNotNull();

        // The dwelling characteristics stay too: a surface and a construction period describe a
        // building, not a person.
        Site site = sites.findById(fixture.siteId()).orElseThrow();
        assertThat(site.observations().surfaceCentiSquareMetres()).isEqualTo(12_000);
        assertThat(site.address().departementCode())
                .as("kept, so the study still resolves against the climate table it used")
                .isEqualTo("69");
    }

    @Test
    void theGeocodeIsHardDeletedRatherThanReplaced() {
        // A BAN geocode is an exact location. ADR-0014 gives it hard delete, not anonymisation:
        // there is no evidential reason to keep where a building stands once the client is gone.
        Fixture fixture = aClientWithAValidatedStudy();

        erasure.erase(fixture.clientId());

        Site site = sites.findById(fixture.siteId()).orElseThrow();
        assertThat(site.address().latitude()).isEmpty();
        assertThat(site.address().longitude()).isEmpty();
    }

    @Test
    void erasureIsRecordedOnBothTheClientAndEverySiteTheyOwn() {
        Fixture fixture = aClientWithAValidatedStudy();

        Client erased = erasure.erase(fixture.clientId());

        assertThat(erased.isAnonymised()).isTrue();
        assertThat(erased.email()).isEmpty();
        assertThat(erased.phone()).isEmpty();
        assertThat(sites.findById(fixture.siteId()).orElseThrow().isAnonymised())
                .as("a delete that misses a site has not deleted anything")
                .isTrue();
    }

    @Test
    void erasingAClientWhoDoesNotExistIsRefusedRatherThanSilentlyDoingNothing() {
        // A request that quietly did nothing would be reported to the data subject as completed.
        assertThatThrownBy(() -> erasure.erase(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to erase");
    }

    /**
     * Every text-ish column of every application table, searched for one value.
     *
     * <p>Discovered from {@code information_schema} rather than listed, so a column added by a later
     * migration is swept automatically. A hand-written list is what misses the denormalised copy.
     */
    private List<String> everyTextColumnContaining(String needle) {
        List<String> hits = new ArrayList<>();
        try (var connection = dataSource.getConnection();
                var columns = connection.createStatement();
                ResultSet rows =
                        columns.executeQuery(
                                "select table_name, column_name from information_schema.columns"
                                        + " where table_schema = 'public'"
                                        + "   and table_name not like 'flyway%'"
                                        + "   and data_type in ('text', 'character varying')")) {
            List<String[]> targets = new ArrayList<>();
            while (rows.next()) {
                targets.add(new String[] {rows.getString("table_name"), rows.getString("column_name")});
            }
            for (String[] target : targets) {
                try (var probe =
                        connection.prepareStatement(
                                "select count(*) from " + target[0] + " where " + target[1] + " like ?")) {
                    probe.setString(1, "%" + needle + "%");
                    try (ResultSet count = probe.executeQuery()) {
                        count.next();
                        if (count.getInt(1) > 0) {
                            hits.add(target[0] + "." + target[1]);
                        }
                    }
                }
            }
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
        return hits;
    }
}
