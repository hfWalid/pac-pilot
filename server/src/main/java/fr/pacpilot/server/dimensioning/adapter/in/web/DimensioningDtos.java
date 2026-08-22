package fr.pacpilot.server.dimensioning.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/** The Dimensioning context's HTTP shapes. Owned by the web adapter; no domain type crosses. */
final class DimensioningDtos {

    private DimensioningDtos() {}

    record RunStudyRequest(
            UUID id,
            UUID siteId,
            int surfaceCentiSquareMetres,
            int ceilingHeightCentimetres,
            String constructionPeriod,
            String insulationLevel,
            String ventilationType,
            String emitterType,
            String climateZone,
            int baseTemperatureDeciCelsius,
            int targetIndoorTemperatureDeciCelsius,
            int electricalSupplyKva,
            String effectiveDate) {}

    /**
     * One response shape for both outcomes, discriminated by [outcome].
     *
     * <p><b>A refusal is a success.</b> {@code MANUAL_STUDY_REQUIRED} means the method declined for a
     * dwelling outside its validated envelope — the request was well-formed, the server worked, and
     * the honest answer is "not this one" (PRODUCT-VIEWS #9). Returning 4xx or 5xx would tell the PWA
     * something false and produce an error screen where M7 needs an explanatory banner.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StudyResponse(
            String outcome,
            UUID id,
            String heatLoadKw,
            String powerBandMinimumKw,
            String powerBandMaximumKw,
            String recommendedFlowTemperatureCelsius,
            /** INDICATIVE while any applied coefficient is unsourced. M5 and M7 both surface this. */
            String confidence,
            boolean provisional,
            List<AssumptionResponse> assumptions,
            List<String> refusalReasons,
            List<String> refusalStatements) {

        static final String COMPUTED = "COMPUTED";
        static final String MANUAL_STUDY_REQUIRED = "MANUAL_STUDY_REQUIRED";
    }

    record AssumptionResponse(String statement, String source, boolean provisional) {}

    record ValidateStudyRequest(UUID installerId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ValidationResponse(UUID id, UUID validatedBy, String validatedAt) {}
}
