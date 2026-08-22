package fr.pacpilot.server.quoting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.core.aids.model.AidLine;
import fr.pacpilot.core.aids.model.AidRuleId;
import fr.pacpilot.core.aids.model.AidRulePackVersion;
import fr.pacpilot.core.aids.model.ResolvedAids;
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
import fr.pacpilot.core.quoting.model.LineItem;
import fr.pacpilot.core.quoting.model.ProductSnapshot;
import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.core.quoting.model.QuoteStatus;
import fr.pacpilot.core.shared.CeilingHeightM;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.DimensioningId;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
import fr.pacpilot.core.shared.InstallerId;
import fr.pacpilot.core.shared.InstantUtc;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import fr.pacpilot.core.shared.PowerBand;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.ProductId;
import fr.pacpilot.core.shared.QuoteId;
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
import fr.pacpilot.server.quoting.application.port.out.CorruptQuoteException;
import fr.pacpilot.server.quoting.application.port.out.QuoteRepository;
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
 * The devis is the artefact that leaves the system. These tests pin the three properties it depends
 * on years later: reproducibility, independence from the catalogue, and internal consistency.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class QuotePersistenceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private QuoteRepository quotes;
    @Autowired private DimensioningRepository studies;
    @Autowired private SiteRepository sites;
    @Autowired private ClientRepository clients;
    @Autowired private InstallerRepository installers;
    @Autowired private DataSource dataSource;

    /** The date whose formula set produced the study — recorded on the aggregate since M4-07. */
    private static final EffectiveDate STUDY_DATE = new EffectiveDate(2026, 8, 22);

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final AtomicLong NEXT_SIRET = new AtomicLong(50_000_000_000_001L);
    private static final AidRulePackVersion PACK = new AidRulePackVersion("sample-2025-H1");
    private static final Percentage REDUCED_VAT = new Percentage(550);

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

    private UUID aSite(UUID installerId) {
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

    private ValidatedDimensioning aValidatedStudy() {
        UUID installerId = anInstaller();
        var computed =
                Dimensioning.Companion.computed(
                        new DimensioningId(UUID.randomUUID().toString()),
                        new SiteId(aSite(installerId).toString()),
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
                                                List.of(new Assumption("U-value", "SOURCE_TBD (provisional)"))))),
                        STUDY_DATE);
        var validated =
                computed.validate(
                        new InstallerId(installerId.toString()),
                        new InstantUtc(Instant.parse("2026-08-22T14:30:00Z").toEpochMilli()));
        studies.save(validated);
        return validated;
    }

    private Quote aDraftQuote(ValidatedDimensioning study) {
        return Quote.Companion.draft(
                new QuoteId(UUID.randomUUID().toString()),
                study,
                new ProductSnapshot(
                        new ProductId("product-1"),
                        "Aquarea T-CAP 12 kW",
                        new PowerKw(12_000),
                        MoneyEur.Companion.ofEuros(9_500)),
                List.of(
                        new LineItem("PAC air-eau 12 kW", MoneyEur.Companion.ofEuros(9_500), 1, REDUCED_VAT),
                        new LineItem("Pose et mise en service", MoneyEur.Companion.ofEuros(2_500), 1, REDUCED_VAT),
                        new LineItem("Radiateur basse temperature", MoneyEur.Companion.ofEuros(500), 4, REDUCED_VAT)),
                new ResolvedAids(
                        PACK,
                        List.of(
                                new AidLine(
                                        new AidRuleId("sample-income-tiered"),
                                        "Aide indexee sur le decile",
                                        MoneyEur.Companion.ofEuros(4_000),
                                        "SOURCE_TBD (sample pack)"),
                                new AidLine(
                                        new AidRuleId("sample-forfait"),
                                        "Forfait fixe",
                                        MoneyEur.Companion.ofEuros(500),
                                        "SOURCE_TBD (sample pack)"))),
                new EffectiveDate(2025, 3, 1));
    }

    @Test
    void aDevisRoundTripsWithEveryLineProductAttributeAndAidIntact() {
        Quote saved = aDraftQuote(aValidatedStudy());
        quotes.save(saved);

        Quote loaded = quotes.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getLines()).isEqualTo(saved.getLines());
        assertThat(loaded.getProduct()).isEqualTo(saved.getProduct());
        assertThat(loaded.getResolvedAids()).isEqualTo(saved.getResolvedAids());
        assertThat(loaded.getEffectiveDate()).isEqualTo(saved.getEffectiveDate());
        assertThat(loaded.getStatus()).isEqualTo(QuoteStatus.DRAFT);
    }

    @Test
    void everyTotalIsDerivedOnLoadRatherThanStored() {
        // 9 500 + 2 500 + (500 x 4) = 14 000 HT; TVA 5,5 % = 770,00; TTC = 14 770,00;
        // less 4 500 in aids = 10 270,00 reste-a-charge.
        Quote saved = aDraftQuote(aValidatedStudy());
        quotes.save(saved);

        Quote loaded = quotes.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getSubtotalExcludingVat().render()).isEqualTo("14000.00");
        assertThat(loaded.getVat().render()).isEqualTo("770.00");
        assertThat(loaded.getTotalIncludingVat().render()).isEqualTo("14770.00");
        assertThat(loaded.getResteACharge().getAmount().render()).isEqualTo("10270.00");
        assertThat(loaded.getResteACharge().getPackVersion()).isEqualTo(PACK);
    }

    @Test
    void noTotalColumnExistsToDriftFromTheLines() throws Exception {
        // The rule PAC-55 names as the one under pressure. A reporting query will want a total
        // column; this asserts nobody has quietly added one.
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "select column_name from information_schema.columns"
                                        + " where table_name = 'quoting_quote'");
                var rows = statement.executeQuery()) {
            while (rows.next()) {
                assertThat(rows.getString("column_name"))
                        .as("a stored total is a second source of truth that drifts from its lines")
                        .doesNotContain("total")
                        .doesNotContain("subtotal")
                        .doesNotContain("reste");
            }
        }
    }

    @Test
    void theProductSnapshotIsIndependentOfWhateverTheCatalogueDoesNext() {
        // The devis in the client's file must not change because a machine was discontinued or a
        // price revised. Nothing joins to a catalogue here — the columns are the proof.
        Quote saved = aDraftQuote(aValidatedStudy());
        quotes.save(saved);

        Quote loaded = quotes.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getProduct().getModel()).isEqualTo("Aquarea T-CAP 12 kW");
        assertThat(loaded.getProduct().getPriceAtQuoteTime().render()).isEqualTo("9500.00");
        assertThat(loaded.getProduct().getPowerAtMinusSevenC().render()).isEqualTo("12.000");
    }

    @Test
    void aStoredDevisStillReachesItsBaremeVersionAndItsStudyInputs() {
        // Reproducibility, end to end: from the loaded devis alone, both halves of the audit chain
        // are reachable — which barème priced it, and what the study assumed.
        Quote saved = aDraftQuote(aValidatedStudy());
        quotes.save(saved);

        Quote loaded = quotes.findById(UUID.fromString(saved.getId().getValue())).orElseThrow();

        assertThat(loaded.getResolvedAids().getPackVersion()).isEqualTo(PACK);
        assertThat(loaded.getDimensioning().getInputs()).isEqualTo(saved.getDimensioning().getInputs());
        assertThat(loaded.getDimensioning().getValidation()).isNotNull();
    }

    @Test
    void aStatusSurvivesTheRoundTripAndTheMachineStillRefusesIllegalMoves() {
        // The status is replayed through transitionTo on load, never written back in. A devis that
        // reached SENT must still refuse to go back to DRAFT.
        Quote sent =
                aDraftQuote(aValidatedStudy())
                        .transitionTo(QuoteStatus.QUOTED)
                        .transitionTo(QuoteStatus.AIDS_RESOLVED)
                        .transitionTo(QuoteStatus.SENT);
        quotes.save(sent);

        Quote loaded = quotes.findById(UUID.fromString(sent.getId().getValue())).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(QuoteStatus.SENT);
        assertThatThrownBy(() -> loaded.transitionTo(QuoteStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(loaded.transitionTo(QuoteStatus.ACCEPTED).getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void aTerminalStatusReplaysCorrectlyDownTheBranchItActuallyTook() {
        // REJECTED and ACCEPTED share a path up to SENT. Replay must pick the branch the stored row
        // records, not the first one it finds.
        Quote rejected =
                aDraftQuote(aValidatedStudy())
                        .transitionTo(QuoteStatus.QUOTED)
                        .transitionTo(QuoteStatus.AIDS_RESOLVED)
                        .transitionTo(QuoteStatus.SENT)
                        .transitionTo(QuoteStatus.REJECTED);
        quotes.save(rejected);

        Quote loaded = quotes.findById(UUID.fromString(rejected.getId().getValue())).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(loaded.getStatus().getAllowedNext()).isEmpty();
    }

    @Test
    void aDevisPointingAtAnUnsignedStudyFailsLoudlyRatherThanLoadingDegraded() throws Exception {
        // ARCHITECTURE #7 allows only Validated → Quoted, so such a row could not have been written
        // by any legitimate path. Reporting it as "no devis here" would be a quieter untruth than
        // saying the row is corrupt.
        Quote saved = aDraftQuote(aValidatedStudy());
        quotes.save(saved);
        UUID quoteId = UUID.fromString(saved.getId().getValue());

        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareStatement(
                                "update dimensioning_study set validated_by = null, validated_at = null"
                                        + " where id = (select dimensioning_id from quoting_quote where id = ?)")) {
            statement.setObject(1, quoteId);
            statement.executeUpdate();
        }

        // Not IllegalStateException: Spring's @Repository translation maps that onto
        // InvalidDataAccessApiUsageException, which would report a broken audit chain as an API
        // misuse by the caller. A dedicated type passes through untranslated.
        assertThatThrownBy(() -> quotes.findById(quoteId))
                .isInstanceOf(CorruptQuoteException.class)
                .hasMessageContaining("missing or unsigned");
    }
}
