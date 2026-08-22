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
import fr.pacpilot.core.dimensioning.model.VentilationType;
import fr.pacpilot.core.dimensioning.port.RunDimensioning;
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
import fr.pacpilot.server.dimensioning.api.StudyVerification;
import fr.pacpilot.server.dimensioning.api.VerificationVerdict;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
import fr.pacpilot.server.dimensioning.application.port.out.VerificationRecord;
import fr.pacpilot.server.dimensioning.application.port.out.VerificationRecordRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The recompute-and-compare path of {@code CLAUDE.md} §4.2, exercised through the application layer
 * at M4 so that M8 only has to change what triggers it.
 *
 * <p>The golden vectors already prove the JVM and JS targets agree on the same inputs. This proves
 * something different: that a result <i>stored</i> against a set of inputs is the result those
 * inputs actually produce — which is what catches a device sending a figure it did not compute.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class StudyVerificationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private StudyVerification verification;
    @Autowired private DimensioningRepository studies;
    @Autowired private VerificationRecordRepository records;
    @Autowired private RunDimensioning engine;
    @Autowired private SiteRepository sites;
    @Autowired private ClientRepository clients;
    @Autowired private InstallerRepository installers;

    private static final EffectiveDate STUDY_DATE = new EffectiveDate(2026, 8, 22);
    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final AtomicLong NEXT_SIRET = new AtomicLong(20_000_000_000_001L);

    private static final InputsSnapshot INPUTS =
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
                    new ElectricalSupplyKva(9));

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
                                RECORDED_AT, RECORDED_AT, Optional.empty()))
                .id();
    }

    /** A study whose stored result is whatever the engine genuinely produces for these inputs. */
    private ComputedDimensioning anHonestStudy() {
        var outcome = engine.run(INPUTS, STUDY_DATE);
        assertThat(outcome).isInstanceOf(DimensioningOutcome.Computed.class);
        var study =
                Dimensioning.Companion.computed(
                        new DimensioningId(UUID.randomUUID().toString()),
                        new SiteId(aSite().toString()),
                        INPUTS,
                        (DimensioningOutcome.Computed) outcome,
                        STUDY_DATE);
        studies.save(study);
        return study;
    }

    /** A study whose stored heat load is one watt off — a device that sent what it did not compute. */
    private ComputedDimensioning aTamperedStudy() {
        var honest = (DimensioningOutcome.Computed) engine.run(INPUTS, STUDY_DATE);
        HeatLoadResult truth = honest.getResult();
        var study =
                Dimensioning.Companion.computed(
                        new DimensioningId(UUID.randomUUID().toString()),
                        new SiteId(aSite().toString()),
                        INPUTS,
                        new DimensioningOutcome.Computed(
                                new HeatLoadResult(
                                        new PowerKw(truth.getHeatLoad().getWatts() - 1),
                                        truth.getRecommendedPowerBand(),
                                        truth.getRecommendedFlowTemperature(),
                                        truth.getAssumptions())),
                        STUDY_DATE);
        studies.save(study);
        return study;
    }

    @Test
    void anHonestStudyVerifiesAndTheVerdictIsRecorded() {
        ComputedDimensioning study = anHonestStudy();
        UUID id = UUID.fromString(study.getId().getValue());

        assertThat(verification.verify(id)).isInstanceOf(VerificationVerdict.Matched.class);
        assertThat(records.findByStudyId(id))
                .singleElement()
                .extracting(VerificationRecord::outcome)
                .isEqualTo(VerificationRecord.MATCHED);
    }

    @Test
    void aDivergenceNamesTheFieldAndBothValues() {
        // "Mismatch" sends someone to a debugger; a named field with both values sends them to the
        // formula set.
        ComputedDimensioning study = aTamperedStudy();

        var verdict = verification.verify(UUID.fromString(study.getId().getValue()));

        assertThat(verdict).isInstanceOf(VerificationVerdict.Diverged.class);
        var diverged = (VerificationVerdict.Diverged) verdict;
        assertThat(diverged.differences()).hasSize(1);
        assertThat(diverged.differences().getFirst().field()).isEqualTo("heatLoad");
        assertThat(diverged.render()).contains("stored 19.031", "recomputed 19.032");
    }

    @Test
    void aDivergenceIsPersistedAndTheStoredResultIsLeftExactlyAsItWas() {
        // The standing rule: persist and flag, never correct. The stored result is what an installer
        // signed; a server that rewrote it would be replacing evidence rather than checking it.
        ComputedDimensioning study = aTamperedStudy();
        UUID id = UUID.fromString(study.getId().getValue());
        String before = study.getResult().getHeatLoad().render();

        verification.verify(id);

        assertThat(records.findByStudyId(id))
                .singleElement()
                .satisfies(
                        record -> {
                            assertThat(record.outcome()).isEqualTo(VerificationRecord.DIVERGED);
                            assertThat(record.differences()).isPresent();
                        });
        assertThat(studies.findById(id).orElseThrow().getResult().getHeatLoad().render())
                .as("the stored result must be untouched by verification")
                .isEqualTo(before);
    }

    @Test
    void recomputationUsesTheStudysOwnDateRatherThanToday() {
        // The reason the effective date was added to the aggregate at M4-07. A study recorded under
        // one version of the method must be recomputed under that version — otherwise every method
        // change would report a divergence on every past study.
        ComputedDimensioning study = anHonestStudy();
        assertThat(study.getEffectiveDate()).isEqualTo(STUDY_DATE);

        var reloaded = studies.findById(UUID.fromString(study.getId().getValue())).orElseThrow();

        assertThat(reloaded.getEffectiveDate())
                .as("the date travels with the study, so verification can reach for the right method")
                .isEqualTo(STUDY_DATE);
        assertThat(verification.verify(UUID.fromString(study.getId().getValue())))
                .isInstanceOf(VerificationVerdict.Matched.class);
    }

    @Test
    void verifyingAgainAddsAVerdictRatherThanReplacingTheLastOne() {
        // A row per run, not per study. A study verified again after a method correction has two
        // verdicts and both are interesting; rewriting one would be indistinguishable from never
        // having found it.
        ComputedDimensioning study = anHonestStudy();
        UUID id = UUID.fromString(study.getId().getValue());

        verification.verify(id);
        verification.verify(id);

        assertThat(records.findByStudyId(id)).hasSize(2);
    }

    @Test
    void verifyingAStudyThatDoesNotExistIsACallerErrorNotADivergence() {
        assertThatThrownBy(() -> verification.verify(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no study");
    }

    @Test
    void theVerdictTypeHasNoCaseForACorrectedValue() {
        // Structural, not behavioural: VerificationVerdict is sealed on Matched | Diverged |
        // NotVerifiable. There is no fourth case, so no code path can express "recomputed and fixed".
        assertThat(VerificationVerdict.class.getPermittedSubclasses())
                .extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("Matched", "Diverged", "NotVerifiable");
    }

    @Test
    void anUnverifiableResultIsNeverReportedAsAMatch() {
        // NotVerifiable is the honest answer when there is nothing to verify against. Collapsing it
        // into Matched would report an unverified result as verified, which is the one outcome this
        // product cannot produce.
        var notVerifiable = new VerificationVerdict.NotVerifiable("no validated method in force");

        assertThat(notVerifiable).isNotInstanceOf(VerificationVerdict.Matched.class);
        assertThatThrownBy(() -> new VerificationVerdict.NotVerifiable("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificationVerdict.Diverged(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
