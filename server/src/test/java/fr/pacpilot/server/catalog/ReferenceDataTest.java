package fr.pacpilot.server.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.dimensioning.model.Assumption;
import fr.pacpilot.core.dimensioning.model.AssumptionsLog;
import fr.pacpilot.core.dimensioning.model.ConstructionPeriod;
import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.core.dimensioning.model.DimensioningOutcome;
import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.dimensioning.model.HeatLoadResult;
import fr.pacpilot.core.dimensioning.model.InputsSnapshot;
import fr.pacpilot.core.dimensioning.model.InsulationLevel;
import fr.pacpilot.core.dimensioning.model.VentilationType;
import fr.pacpilot.core.shared.CeilingHeightM;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.DimensioningId;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
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
import java.util.Optional;
import java.util.UUID;
import fr.pacpilot.server.catalog.api.CatalogProduct;
import fr.pacpilot.server.catalog.api.ClimateReference;
import fr.pacpilot.server.catalog.api.ProductCatalogue;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reference data, reproduced from the migrations alone.
 *
 * <p>The load-bearing assertion here is the one about the climate table being <b>empty</b>. It is
 * not an oversight to be filled in later by whoever notices — it is PAC-56's instruction not to
 * invent tabulated values, and a test is the only thing that stops a well-meaning follow-up from
 * seeding plausible numbers.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class ReferenceDataTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private ClimateReference climates;
    @Autowired private ProductCatalogue catalogue;
    @Autowired private DataSource dataSource;
    @Autowired private DimensioningRepository studies;
    @Autowired private SiteRepository sites;
    @Autowired private ClientRepository clients;
    @Autowired private InstallerRepository installers;

    @Test
    void noDepartementClimateRowIsSeeded_becauseNoVerifiedSourceExists() {
        // The outdoor design temperature feeds straight into every heat load. A plausible value here
        // would be an unsourced number that looks authoritative — CLAUDE.md §12's failure mode, at
        // the point where it does the most damage. An empty table refuses visibly; a filled one does
        // not (PAC-56).
        assertThat(rowCount("catalog_departement_climate"))
                .as("no verified source for the departement base temperatures exists yet")
                .isZero();
        assertThat(climates.forDepartement("69")).isEmpty();
    }

    @Test
    void everySeededValueCarriesASource() {
        // The ticket's SQL check, as a test. Enforced by constraint too, but a constraint only stops
        // a blank — this stops a follow-up migration adding a row with no provenance at all.
        assertThat(rowCount("catalog_departement_climate where source is null or trim(source) = ''"))
                .isZero();
        assertThat(rowCount("catalog_product where source is null or trim(source) = ''")).isZero();
    }

    @Test
    void everySeededProductDeclaresItselfProvisional() {
        // Seeded rows are synthetic. A caller putting one on a devis is doing something the catalogue
        // cannot back, and isProvisional is what lets it find that out.
        List<CatalogProduct> all = catalogue.withinBand(new PowerKw(0), new PowerKw(1_000_000));

        assertThat(all).isNotEmpty();
        assertThat(all).allMatch(CatalogProduct::isProvisional);
        assertThat(all).allMatch(product -> product.brand().equals("ECHANTILLON"));
    }

    @Test
    void productsAreSelectedByTheRecommendedBandRatherThanTheBareLoad() {
        // Under-sizing leaves a cold client in February; over-sizing short-cycles the machine to an
        // early death. The band carries that asymmetry (M2-04); the heat load alone does not.
        List<CatalogProduct> inBand = catalogue.withinBand(new PowerKw(7_000), new PowerKw(13_000));

        assertThat(inBand).extracting(CatalogProduct::id).containsExactly("echantillon-08", "echantillon-12");
    }

    @Test
    void aProductRoundTripsWithItsEmitterCompatibility() {
        CatalogProduct product = catalogue.findById("echantillon-12").orElseThrow();

        assertThat(product.powerAtMinusSevenC().render()).isEqualTo("12.000");
        assertThat(product.compatibleEmitters())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "RADIATOR_HIGH_TEMPERATURE", "RADIATOR_LOW_TEMPERATURE", "UNDERFLOOR_HEATING");
    }

    @Test
    void correctingABaseTemperatureLeavesAlreadyStoredStudiesUntouched() {
        // The reason InputsSnapshot records the RESOLVED temperature and not just the zone (M1-04).
        // A tabulated value being corrected is normal — it is reference data — and it must not
        // silently restate what a signed study was computed from. Without this property, a
        // correction would quietly rewrite the basis of every past devis.
        execute(
                "insert into catalog_departement_climate"
                        + " (departement_code, zone, base_temperature_deci_c, source)"
                        + " values ('69', 'H1', -70, 'SOURCE_TBD (test fixture, not a tabulated value)')");

        // Resolve at the boundary, exactly as an adapter would, and freeze it into the snapshot.
        TemperatureC resolved = climates.forDepartement("69").orElseThrow().getBaseTemperature();
        assertThat(resolved.render()).isEqualTo("-7.0");

        var study =
                Dimensioning.Companion.computed(
                        new DimensioningId(UUID.randomUUID().toString()),
                        new SiteId(aSite().toString()),
                        new InputsSnapshot(
                                new SurfaceM2(12_000),
                                new CeilingHeightM(250),
                                ConstructionPeriod.BEFORE_1975,
                                InsulationLevel.PARTIAL,
                                VentilationType.VMC_SIMPLE_FLUX,
                                EmitterType.RADIATOR_HIGH_TEMPERATURE,
                                ClimateZone.H1,
                                resolved,
                                new TemperatureC(190),
                                new ElectricalSupplyKva(9)),
                        new DimensioningOutcome.Computed(
                                new HeatLoadResult(
                                        new PowerKw(19_032),
                                        new PowerBand(new PowerKw(17_129), new PowerKw(22_838)),
                                        new TemperatureC(500),
                                        new AssumptionsLog(
                                                java.util.List.of(
                                                        new Assumption("U-value", "SOURCE_TBD (provisional)"))))),
                        STUDY_DATE);
        studies.save(study);

        // A later migration corrects the tabulated value.
        execute("update catalog_departement_climate set base_temperature_deci_c = -80 where departement_code = '69'");

        var reloaded = studies.findById(UUID.fromString(study.getId().getValue())).orElseThrow();
        assertThat(reloaded.getInputs().getBaseTemperature().render())
                .as("a stored study is computed from what it recorded, not from the table as it stands today")
                .isEqualTo("-7.0");
        assertThat(climates.forDepartement("69").orElseThrow().getBaseTemperature().render())
                .as("while a new study picks up the corrected value")
                .isEqualTo("-8.0");

        execute("delete from catalog_departement_climate where departement_code = '69'");
    }

    /** The date whose formula set produced the study — recorded on the aggregate since M4-07. */
    private static final EffectiveDate STUDY_DATE = new EffectiveDate(2026, 8, 22);

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final java.util.concurrent.atomic.AtomicLong NEXT_SIRET =
            new java.util.concurrent.atomic.AtomicLong(30_000_000_000_001L);

    private UUID aSite() {
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
        return sites.save(
                        new Site(
                                UUID.randomUUID(),
                                client.id(),
                                new SiteAddress(
                                        "12 rue des Lilas", "69003", "Lyon", "69",
                                        Optional.empty(), Optional.empty()),
                                new DwellingObservations(
                                        12_000, 250, "BEFORE_1975", "PARTIAL", "VMC_SIMPLE_FLUX",
                                        "RADIATOR_HIGH_TEMPERATURE", 9),
                                RECORDED_AT,
                                RECORDED_AT,
                                Optional.empty()))
                .id();
    }

    private void execute(String sql) {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private int rowCount(String fromClause) {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select count(*) from " + fromClause)) {
            rows.next();
            return rows.getInt(1);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }
}
