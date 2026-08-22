package fr.pacpilot.server.dimensioning.adapter.in.web;

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
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.ElectricalSupplyKva;
import fr.pacpilot.core.shared.SurfaceM2;
import fr.pacpilot.core.shared.TemperatureC;
import fr.pacpilot.server.dimensioning.adapter.in.web.DimensioningDtos.AssumptionResponse;
import fr.pacpilot.server.dimensioning.adapter.in.web.DimensioningDtos.RunStudyRequest;
import fr.pacpilot.server.dimensioning.adapter.in.web.DimensioningDtos.StudyResponse;
import fr.pacpilot.server.dimensioning.adapter.in.web.DimensioningDtos.ValidateStudyRequest;
import fr.pacpilot.server.dimensioning.adapter.in.web.DimensioningDtos.ValidationResponse;
import fr.pacpilot.server.dimensioning.application.RunStudy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The heat-loss study, over HTTP.
 *
 * <p>Two operations, and the second is a separate endpoint rather than a PATCH on the first because
 * validation is a distinct act with distinct legal weight ({@code CLAUDE.md} §4.5) — an artisan
 * signing a study is not editing a field on it.
 *
 * <p>Nothing here decides anything. Enum parsing and unit conversion are translation, not rules; the
 * method's own judgement lives in the engine, and a controller that grew a rule would break the SDK
 * bet of §3 by making REST the only way in.
 */
@RestController
@RequestMapping("/api/dimensionings")
class DimensioningController {

    private final RunStudy studies;

    DimensioningController(RunStudy studies) {
        this.studies = studies;
    }

    @PostMapping
    ResponseEntity<StudyResponse> run(@RequestBody RunStudyRequest request) {
        InputsSnapshot inputs =
                new InputsSnapshot(
                        new SurfaceM2(request.surfaceCentiSquareMetres()),
                        new CeilingHeightM(request.ceilingHeightCentimetres()),
                        ConstructionPeriod.valueOf(request.constructionPeriod()),
                        InsulationLevel.valueOf(request.insulationLevel()),
                        VentilationType.valueOf(request.ventilationType()),
                        EmitterType.valueOf(request.emitterType()),
                        ClimateZone.valueOf(request.climateZone()),
                        new TemperatureC(request.baseTemperatureDeciCelsius()),
                        new TemperatureC(request.targetIndoorTemperatureDeciCelsius()),
                        new ElectricalSupplyKva(request.electricalSupplyKva()));

        LocalDate date = LocalDate.parse(request.effectiveDate());
        RunStudy.Outcome outcome =
                studies.run(
                        request.id(),
                        request.siteId(),
                        inputs,
                        new EffectiveDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth()));

        // 200 for both branches. A refusal is an answer, not a fault — see StudyResponse.
        return ResponseEntity.ok(toResponse(outcome));
    }

    @PostMapping("/{id}/validation")
    ResponseEntity<ValidationResponse> validate(
            @PathVariable UUID id, @RequestBody ValidateStudyRequest request) {
        Dimensioning validated = studies.validate(id, request.installerId());
        var act = ((ValidatedDimensioning) validated).getValidation();

        return ResponseEntity.ok(
                new ValidationResponse(
                        id,
                        UUID.fromString(act.getValidatedBy().getValue()),
                        Instant.ofEpochMilli(act.getValidatedAt().getEpochMilliseconds()).toString()));
    }

    private static StudyResponse toResponse(RunStudy.Outcome outcome) {
        if (outcome.outcome() instanceof DimensioningOutcome.Computed computed) {
            HeatLoadResult result = computed.getResult();
            return new StudyResponse(
                    StudyResponse.COMPUTED,
                    outcome.storedAs(),
                    result.getHeatLoad().render(),
                    result.getRecommendedPowerBand().getMinimum().render(),
                    result.getRecommendedPowerBand().getMaximum().render(),
                    result.getRecommendedFlowTemperature() == null
                            ? null
                            : result.getRecommendedFlowTemperature().render(),
                    result.getConfidence().name(),
                    result.isProvisional(),
                    result.getAssumptions().getEntries().stream()
                            .map(
                                    assumption ->
                                            new AssumptionResponse(
                                                    assumption.getStatement(),
                                                    assumption.getSource(),
                                                    assumption.isProvisional()))
                            .toList(),
                    null,
                    null);
        }

        var refusal = (DimensioningOutcome.ManualStudyRequired) outcome.outcome();
        return new StudyResponse(
                StudyResponse.MANUAL_STUDY_REQUIRED,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                refusal.getReasons().stream().map(Enum::name).toList(),
                // The domain's own wording, in the language the installer works in. An adapter may
                // re-render it; what it must not do is invent a different meaning.
                refusal.getReasons().stream().map(reason -> reason.getStatement()).toList());
    }
}
