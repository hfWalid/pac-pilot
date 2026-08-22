package fr.pacpilot.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The whole pre-visit flow over real HTTP against a real PostgreSQL: client → site → study →
 * validation → devis.
 *
 * <p>Serves PAC-58 and PAC-59 together. They ask for the same thing from two directions — one that
 * the endpoints exist and behave, one that the flow runs end to end — and splitting them would mean
 * two suites driving the identical sequence.
 *
 * <p><b>The flow ends in a refusal, and that is the honest state of the product today.</b> No barème
 * is published (ADR-0017), so no devis can be priced. This suite asserts the refusal precisely rather
 * than skipping the step, because a test that quietly stopped before the last call would let the gap
 * go unnoticed.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "pacpilot.dimensioning.method=indicative-provisional")
class PreVisitFlowApiTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private TestRestTemplate http;

    private static final AtomicLong NEXT_SIRET = new AtomicLong(60_000_000_000_001L);

    /** A decile whose digit appears nowhere else in any fixture, so a leak is unmistakable. */
    private static final int TELLTALE_DECILE = 7;

    private UUID anInstaller() {
        UUID id = UUID.randomUUID();
        // Identity has no endpoint until M10; the account of record is seeded straight through the
        // repository so the flow has something to attribute work to (ADR-0013).
        installerRepository.save(
                new fr.pacpilot.server.identity.domain.Installer(
                        id,
                        "Chauffage Berthier",
                        new fr.pacpilot.server.identity.domain.Siret(
                                String.format("%014d", NEXT_SIRET.getAndIncrement())),
                        java.util.Optional.empty(),
                        java.time.Instant.parse("2026-08-22T09:00:00Z"),
                        java.time.Instant.parse("2026-08-22T09:00:00Z")));
        return id;
    }

    @Autowired
    private fr.pacpilot.server.identity.application.port.out.InstallerRepository installerRepository;

    private JsonNode post(String path, Map<String, ?> body, HttpStatus expected) {
        ResponseEntity<JsonNode> response = http.postForEntity(path, body, JsonNode.class);
        assertThat(response.getStatusCode()).as("POST %s", path).isEqualTo(expected);
        return response.getBody();
    }

    @Test
    void theWholePreVisitFlowRunsEndToEndOverHttp() {
        UUID installerId = anInstaller();

        // 1. The client. The device owns identity: the id goes in and must come back unchanged.
        UUID clientId = UUID.randomUUID();
        JsonNode client =
                post(
                        "/api/clients",
                        Map.of(
                                "id", clientId,
                                "installerId", installerId,
                                "firstName", "Camille",
                                "lastName", "Berthier",
                                "email", "camille.berthier@example.fr"),
                        HttpStatus.CREATED);
        assertThat(client.get("id").asText()).isEqualTo(clientId.toString());

        // 2. The site, ungeocoded — the installer is in a cellar and geocoding never blocks.
        UUID siteId = UUID.randomUUID();
        JsonNode site =
                post(
                        "/api/sites",
                        Map.ofEntries(
                                Map.entry("id", siteId),
                                Map.entry("clientId", clientId),
                                Map.entry("addressLine", "12 rue des Lilas"),
                                Map.entry("postcode", "69003"),
                                Map.entry("commune", "Lyon"),
                                Map.entry("departementCode", "69"),
                                Map.entry("surfaceCentiSquareMetres", 12_000),
                                Map.entry("ceilingHeightCentimetres", 250),
                                Map.entry("constructionPeriod", "BEFORE_1975"),
                                Map.entry("insulationLevel", "PARTIAL"),
                                Map.entry("ventilationType", "VMC_SIMPLE_FLUX"),
                                Map.entry("emitterType", "RADIATOR_HIGH_TEMPERATURE"),
                                Map.entry("electricalSupplyKva", 9)),
                        HttpStatus.CREATED);
        assertThat(site.get("geocoded").asBoolean()).isFalse();

        // 3. The study.
        UUID studyId = UUID.randomUUID();
        JsonNode study = post("/api/dimensionings", studyRequest(studyId, siteId, 12_000), HttpStatus.OK);

        assertThat(study.get("outcome").asText()).isEqualTo("COMPUTED");
        assertThat(study.get("heatLoadKw").asText()).isEqualTo("19.032");
        assertThat(study.get("confidence").asText())
                .as("the method is provisional, and the wire must say so (ADR-0015)")
                .isEqualTo("INDICATIVE");
        assertThat(study.get("provisional").asBoolean()).isTrue();
        assertThat(study.get("assumptions")).isNotEmpty();

        // 4. Validation — its own endpoint, because signing is an act, not a field update.
        JsonNode validation =
                post(
                        "/api/dimensionings/" + studyId + "/validation",
                        Map.of("installerId", installerId),
                        HttpStatus.OK);
        assertThat(validation.get("validatedBy").asText()).isEqualTo(installerId.toString());
        assertThat(validation.get("validatedAt").asText()).isNotBlank();

        // 5. The devis — refused, because no barème is published (ADR-0017). A success carrying a
        //    refusal, not an error: the request was well-formed and the server worked.
        JsonNode devis = post("/api/quotes", quoteRequest(studyId), HttpStatus.OK);

        assertThat(devis.get("outcome").asText()).isEqualTo("NO_BAREME_PUBLISHED");
        assertThat(devis.get("refusalDate").asText()).isEqualTo("2026-08-22");
        assertThat(devis.has("id")).isFalse();
        assertThat(devis.get("refusalStatement").asText()).contains("bareme");
    }

    @Test
    void aDwellingOutsideTheEnvelopeIsASuccessCarryingTheRefusal() {
        // PRODUCT-VIEWS #9 wants an explicit banner at M7, not an error screen. A 4xx here would tell
        // the PWA something false: the request was fine, the method simply declined.
        UUID siteId = aSite(anInstaller());

        JsonNode study =
                post("/api/dimensionings", studyRequest(UUID.randomUUID(), siteId, 1_000), HttpStatus.OK);

        assertThat(study.get("outcome").asText()).isEqualTo("MANUAL_STUDY_REQUIRED");
        assertThat(study.get("refusalReasons")).isNotEmpty();
        assertThat(study.get("refusalReasons").get(0).asText()).isEqualTo("SURFACE_OUTSIDE_RANGE");
        assertThat(study.get("refusalStatements").get(0).asText())
                .as("the domain's own wording, in the language the installer works in")
                .contains("hors du domaine valide");
        assertThat(study.has("id")).as("a refusal is not persisted; there is nothing to sign").isFalse();
    }

    @Test
    void theIncomeDecileIsNeverEchoedBackByAnyEndpointThatTakesIt() {
        // §4.6 treats fiscal income as sensitive, and a response body is the most rendered surface
        // there is — it reaches a browser, a log, and whatever the PWA caches. Adversarial by design:
        // the decile's digit appears nowhere else in these fixtures.
        UUID studyId = aValidatedStudy();

        JsonNode aids =
                post(
                        "/api/aids/resolutions",
                        Map.of(
                                "incomeDecile", TELLTALE_DECILE,
                                "heatPumpType", "AIR_WATER",
                                "climateZone", "H1",
                                "replacedSystem", "OIL_BOILER",
                                "workCostCents", 1_400_000,
                                "effectiveDate", "2026-08-22"),
                        HttpStatus.OK);
        JsonNode devis = post("/api/quotes", quoteRequest(studyId), HttpStatus.OK);

        assertThat(aids.toString()).doesNotContain("\"" + TELLTALE_DECILE + "\"", "decile");
        assertThat(devis.toString()).doesNotContain("\"" + TELLTALE_DECILE + "\"", "decile");
        assertThat(aids.get("outcome").asText()).isEqualTo("NO_PACK_PUBLISHED");
    }

    @Test
    void aRefusedAidsResolutionIsDistinguishableFromZeroAids() {
        // Zero is a claim about the household; a refusal is a statement about the system. A homeowner
        // told "0 €" would reasonably conclude they qualify for nothing.
        JsonNode aids =
                post(
                        "/api/aids/resolutions",
                        Map.of(
                                "incomeDecile", 3,
                                "heatPumpType", "AIR_WATER",
                                "climateZone", "H1",
                                "replacedSystem", "OIL_BOILER",
                                "workCostCents", 1_400_000,
                                "effectiveDate", "2026-08-22"),
                        HttpStatus.OK);

        assertThat(aids.get("outcome").asText()).isEqualTo("NO_PACK_PUBLISHED");
        assertThat(aids.has("totalAids")).isFalse();
        assertThat(aids.has("estimatedResteACharge")).isFalse();
        assertThat(aids.get("refusalStatement").asText()).isNotBlank();
    }

    @Test
    void theReportIsFetchableAsAPdfOnceTheStudyIsSigned() {
        // M5's renderer, reachable. A document nothing can fetch has not left the system.
        UUID studyId = aValidatedStudy();

        ResponseEntity<byte[]> report =
                http.getForEntity("/api/dimensionings/" + studyId + "/report", byte[].class);

        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(report.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(report.getBody()).isNotNull();
        // %PDF- — a real document rather than an empty 200.
        assertThat(new String(report.getBody(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
    }

    @Test
    void anUnsignedStudyHasNoReportYet() {
        // A 404 is the honest answer: the report carries the validation act, so until someone signs
        // the study there is nothing to put in that section and no report to fetch.
        UUID studyId = UUID.randomUUID();
        post("/api/dimensionings", studyRequest(studyId, aSite(anInstaller()), 12_000), HttpStatus.OK);

        assertThat(http.getForEntity("/api/dimensionings/" + studyId + "/report", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fetchingTheSameReportTwiceReturnsIdenticalBytes() {
        // Rendered on demand and never stored, which is only safe because it is deterministic
        // (PAC-66). The bytes fetched today are the bytes fetched in three years.
        UUID studyId = aValidatedStudy();

        byte[] first = http.getForObject("/api/dimensionings/" + studyId + "/report", byte[].class);
        byte[] second = http.getForObject("/api/dimensionings/" + studyId + "/report", byte[].class);

        assertThat(first).isEqualTo(second);
    }

    // ── fixtures ────────────────────────────────────────────────────────────────────────────

    private Map<String, Object> studyRequest(UUID studyId, UUID siteId, int surfaceCentiM2) {
        return Map.ofEntries(
                Map.entry("id", studyId),
                Map.entry("siteId", siteId),
                Map.entry("surfaceCentiSquareMetres", surfaceCentiM2),
                Map.entry("ceilingHeightCentimetres", 250),
                Map.entry("constructionPeriod", "BEFORE_1975"),
                Map.entry("insulationLevel", "PARTIAL"),
                Map.entry("ventilationType", "VMC_SIMPLE_FLUX"),
                Map.entry("emitterType", "RADIATOR_HIGH_TEMPERATURE"),
                Map.entry("climateZone", "H1"),
                Map.entry("baseTemperatureDeciCelsius", -70),
                Map.entry("targetIndoorTemperatureDeciCelsius", 190),
                Map.entry("electricalSupplyKva", 9),
                Map.entry("effectiveDate", "2026-08-22"));
    }

    private Map<String, Object> quoteRequest(UUID studyId) {
        return Map.ofEntries(
                Map.entry("dimensioningId", studyId),
                Map.entry("effectiveDate", "2026-08-22"),
                Map.entry("incomeDecile", TELLTALE_DECILE),
                Map.entry("heatPumpType", "AIR_WATER"),
                Map.entry("climateZone", "H1"),
                Map.entry("replacedSystem", "OIL_BOILER"),
                Map.entry("workCostCents", 1_400_000),
                Map.entry("productId", "echantillon-12"),
                Map.entry("productModel", "ECHANTILLON 12 kW (donnee non sourcee)"),
                Map.entry("productPowerAtMinusSevenWatts", 12_000),
                Map.entry("productPriceCents", 950_000),
                Map.entry(
                        "lines",
                        java.util.List.of(
                                Map.of(
                                        "label", "PAC air-eau 12 kW",
                                        "unitPriceCents", 950_000,
                                        "quantity", 1,
                                        "vatBasisPoints", 550))));
    }

    private UUID aSite(UUID installerId) {
        UUID clientId = UUID.randomUUID();
        post(
                "/api/clients",
                Map.of(
                        "id", clientId,
                        "installerId", installerId,
                        "firstName", "Camille",
                        "lastName", "Berthier"),
                HttpStatus.CREATED);
        UUID siteId = UUID.randomUUID();
        post(
                "/api/sites",
                Map.ofEntries(
                        Map.entry("id", siteId),
                        Map.entry("clientId", clientId),
                        Map.entry("addressLine", "12 rue des Lilas"),
                        Map.entry("postcode", "69003"),
                        Map.entry("commune", "Lyon"),
                        Map.entry("departementCode", "69"),
                        Map.entry("surfaceCentiSquareMetres", 12_000),
                        Map.entry("ceilingHeightCentimetres", 250),
                        Map.entry("constructionPeriod", "BEFORE_1975"),
                        Map.entry("insulationLevel", "PARTIAL"),
                        Map.entry("ventilationType", "VMC_SIMPLE_FLUX"),
                        Map.entry("emitterType", "RADIATOR_HIGH_TEMPERATURE"),
                        Map.entry("electricalSupplyKva", 9)),
                HttpStatus.CREATED);
        return siteId;
    }

    private UUID aValidatedStudy() {
        UUID installerId = anInstaller();
        UUID studyId = UUID.randomUUID();
        post("/api/dimensionings", studyRequest(studyId, aSite(installerId), 12_000), HttpStatus.OK);
        post(
                "/api/dimensionings/" + studyId + "/validation",
                Map.of("installerId", installerId),
                HttpStatus.OK);
        return studyId;
    }
}
