package fr.pacpilot.server.dimensioning.adapter.out.persistence;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The only class that knows both the row and the aggregate.
 *
 * <p><b>Reconstruction replays the domain's own lifecycle rather than reaching into it.</b> Both
 * cases of the sealed hierarchy have {@code internal} constructors, and the way back in is the same
 * path the PWA takes: {@code Dimensioning.computed(...)} produces the unsigned case, and
 * {@code .validate(by, at)} produces the signed one. So a row loaded from the database goes through
 * exactly the transition an installer went through — every invariant on the way is the same one, and
 * there is no second construction path that could drift from the first.
 *
 * <p>That is also why the nullable column pair maps cleanly. The domain has two types; the table has
 * one row shape with a check constraint. This class is where those meet, and the only place that
 * should know both.
 */
final class DimensioningMapper {

    private DimensioningMapper() {}

    static DimensioningStudyEntity toEntity(Dimensioning study, Instant createdAt) {
        InputsSnapshot inputs = study.getInputs();
        HeatLoadResult result = study.getResult();

        // The signed case is the only one carrying a validation, and the compiler is what enforces
        // that — there is no signature to read on the computed case, so there is nothing to forget.
        UUID validatedBy = null;
        Instant validatedAt = null;
        if (study instanceof ValidatedDimensioning validated) {
            validatedBy = UUID.fromString(validated.getValidation().getValidatedBy().getValue());
            validatedAt = Instant.ofEpochMilli(validated.getValidation().getValidatedAt().getEpochMilliseconds());
        }

        DimensioningStudyEntity entity =
                new DimensioningStudyEntity(
                        UUID.fromString(study.getId().getValue()),
                        UUID.fromString(study.getSiteId().getValue()),
                        inputs.getSurface().getCentiSquareMetres(),
                        inputs.getCeilingHeight().getCentimetres(),
                        inputs.getConstructionPeriod().name(),
                        inputs.getInsulationLevel().name(),
                        inputs.getVentilationType().name(),
                        inputs.getEmitterType().name(),
                        inputs.getClimateZone().name(),
                        inputs.getBaseTemperature().getDeciCelsius(),
                        inputs.getTargetIndoorTemperature().getDeciCelsius(),
                        inputs.getAvailableElectricalPower().getKva(),
                        result.getHeatLoad().getWatts(),
                        result.getRecommendedPowerBand().getMinimum().getWatts(),
                        result.getRecommendedPowerBand().getMaximum().getWatts(),
                        result.getRecommendedFlowTemperature() == null
                                ? null
                                : result.getRecommendedFlowTemperature().getDeciCelsius(),
                        validatedBy,
                        validatedAt,
                        LocalDate.of(
                                study.getEffectiveDate().getYear(),
                                study.getEffectiveDate().getMonth(),
                                study.getEffectiveDate().getDay()),
                        createdAt);

        List<Assumption> entries = result.getAssumptions().getEntries();
        entity.replaceAssumptions(
                IntStream.range(0, entries.size())
                        .mapToObj(
                                position ->
                                        new AssumptionEntity(
                                                position,
                                                entries.get(position).getStatement(),
                                                entries.get(position).getSource()))
                        .toList());
        return entity;
    }

    static Dimensioning toDomain(DimensioningStudyEntity entity) {
        InputsSnapshot inputs =
                new InputsSnapshot(
                        new SurfaceM2(entity.getSurfaceCentiM2()),
                        new CeilingHeightM(entity.getCeilingHeightCm()),
                        ConstructionPeriod.valueOf(entity.getConstructionPeriod()),
                        InsulationLevel.valueOf(entity.getInsulationLevel()),
                        VentilationType.valueOf(entity.getVentilationType()),
                        EmitterType.valueOf(entity.getEmitterType()),
                        ClimateZone.valueOf(entity.getClimateZone()),
                        new TemperatureC(entity.getBaseTemperatureDeciC()),
                        new TemperatureC(entity.getTargetIndoorTemperatureDeciC()),
                        new ElectricalSupplyKva(entity.getAvailableElectricalKva()));

        HeatLoadResult result =
                new HeatLoadResult(
                        new PowerKw(entity.getHeatLoadWatts()),
                        new PowerBand(
                                new PowerKw(entity.getPowerBandMinimumWatts()),
                                new PowerKw(entity.getPowerBandMaximumWatts())),
                        entity.getFlowTemperatureDeciC() == null
                                ? null
                                : new TemperatureC(entity.getFlowTemperatureDeciC()),
                        new AssumptionsLog(
                                entity.getAssumptions().stream()
                                        .map(row -> new Assumption(row.getStatement(), row.getSource()))
                                        .toList()));

        var computed =
                Dimensioning.Companion.computed(
                        new DimensioningId(entity.getId().toString()),
                        new SiteId(entity.getSiteId().toString()),
                        inputs,
                        new DimensioningOutcome.Computed(result),
                        new EffectiveDate(
                                entity.getEffectiveDate().getYear(),
                                entity.getEffectiveDate().getMonthValue(),
                                entity.getEffectiveDate().getDayOfMonth()));

        if (entity.getValidatedBy() == null) {
            return computed;
        }
        return computed.validate(
                new InstallerId(entity.getValidatedBy().toString()),
                new InstantUtc(entity.getValidatedAt().toEpochMilli()));
    }
}
