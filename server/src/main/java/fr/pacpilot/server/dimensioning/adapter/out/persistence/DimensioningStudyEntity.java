package fr.pacpilot.server.dimensioning.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The {@code dimensioning_study} row.
 *
 * <p>Mutable, no-arg-constructible and flat — everything the domain aggregate refuses to be. That
 * is the point of it existing: {@code ComputedDimensioning} and {@code ValidatedDimensioning} are a
 * sealed pair with no {@code copy()}, precisely so a signature cannot end up attached to inputs the
 * signer never saw, and an {@code @Entity} on those types would need exactly the no-arg constructor
 * and setters that reopen that hole.
 *
 * <p>Package-private. Nothing outside this adapter has any reason to hold one.
 */
@Entity
@Table(name = "dimensioning_study")
class DimensioningStudyEntity {

    @Id private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    // ── InputsSnapshot, in exact minor units ────────────────────────────────────────────────
    @Column(name = "surface_centi_m2", nullable = false)
    private int surfaceCentiM2;

    @Column(name = "ceiling_height_cm", nullable = false)
    private int ceilingHeightCm;

    @Column(name = "construction_period", nullable = false)
    private String constructionPeriod;

    @Column(name = "insulation_level", nullable = false)
    private String insulationLevel;

    @Column(name = "ventilation_type", nullable = false)
    private String ventilationType;

    @Column(name = "emitter_type", nullable = false)
    private String emitterType;

    @Column(name = "climate_zone", nullable = false)
    private String climateZone;

    @Column(name = "base_temperature_deci_c", nullable = false)
    private int baseTemperatureDeciC;

    @Column(name = "target_indoor_temp_deci_c", nullable = false)
    private int targetIndoorTemperatureDeciC;

    @Column(name = "available_electrical_kva", nullable = false)
    private int availableElectricalKva;

    // ── HeatLoadResult ──────────────────────────────────────────────────────────────────────
    @Column(name = "heat_load_w", nullable = false)
    private int heatLoadWatts;

    @Column(name = "power_band_minimum_w", nullable = false)
    private int powerBandMinimumWatts;

    @Column(name = "power_band_maximum_w", nullable = false)
    private int powerBandMaximumWatts;

    /** Null means the method declined to advise, which the assumptions log explains. */
    @Column(name = "flow_temperature_deci_c")
    private Integer flowTemperatureDeciC;

    /**
     * Ordered and eagerly fetched, because a study is never useful without its reasoning: {@code
     * HeatLoadResult} refuses to be constructed with an empty log, so a lazily-loaded one outside a
     * session would fail on the way back into the domain rather than on read.
     */
    @OneToMany(
            mappedBy = "study",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<AssumptionEntity> assumptions = new ArrayList<>();

    // ── ValidationAct — both columns or neither, enforced by check constraint ───────────────
    @Column(name = "validated_by")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    /** The date whose formula set produced this result — recompute against this, never today. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DimensioningStudyEntity() {}

    @SuppressWarnings("checkstyle:ParameterNumber")
    DimensioningStudyEntity(
            UUID id,
            UUID siteId,
            int surfaceCentiM2,
            int ceilingHeightCm,
            String constructionPeriod,
            String insulationLevel,
            String ventilationType,
            String emitterType,
            String climateZone,
            int baseTemperatureDeciC,
            int targetIndoorTemperatureDeciC,
            int availableElectricalKva,
            int heatLoadWatts,
            int powerBandMinimumWatts,
            int powerBandMaximumWatts,
            Integer flowTemperatureDeciC,
            UUID validatedBy,
            Instant validatedAt,
            LocalDate effectiveDate,
            Instant createdAt) {
        this.id = id;
        this.siteId = siteId;
        this.surfaceCentiM2 = surfaceCentiM2;
        this.ceilingHeightCm = ceilingHeightCm;
        this.constructionPeriod = constructionPeriod;
        this.insulationLevel = insulationLevel;
        this.ventilationType = ventilationType;
        this.emitterType = emitterType;
        this.climateZone = climateZone;
        this.baseTemperatureDeciC = baseTemperatureDeciC;
        this.targetIndoorTemperatureDeciC = targetIndoorTemperatureDeciC;
        this.availableElectricalKva = availableElectricalKva;
        this.heatLoadWatts = heatLoadWatts;
        this.powerBandMinimumWatts = powerBandMinimumWatts;
        this.powerBandMaximumWatts = powerBandMaximumWatts;
        this.flowTemperatureDeciC = flowTemperatureDeciC;
        this.validatedBy = validatedBy;
        this.validatedAt = validatedAt;
        this.effectiveDate = effectiveDate;
        this.createdAt = createdAt;
    }

    void replaceAssumptions(List<AssumptionEntity> replacements) {
        assumptions.clear();
        replacements.forEach(
                entry -> {
                    entry.attachTo(this);
                    assumptions.add(entry);
                });
    }

    UUID getId() {
        return id;
    }

    UUID getSiteId() {
        return siteId;
    }

    int getSurfaceCentiM2() {
        return surfaceCentiM2;
    }

    int getCeilingHeightCm() {
        return ceilingHeightCm;
    }

    String getConstructionPeriod() {
        return constructionPeriod;
    }

    String getInsulationLevel() {
        return insulationLevel;
    }

    String getVentilationType() {
        return ventilationType;
    }

    String getEmitterType() {
        return emitterType;
    }

    String getClimateZone() {
        return climateZone;
    }

    int getBaseTemperatureDeciC() {
        return baseTemperatureDeciC;
    }

    int getTargetIndoorTemperatureDeciC() {
        return targetIndoorTemperatureDeciC;
    }

    int getAvailableElectricalKva() {
        return availableElectricalKva;
    }

    int getHeatLoadWatts() {
        return heatLoadWatts;
    }

    int getPowerBandMinimumWatts() {
        return powerBandMinimumWatts;
    }

    int getPowerBandMaximumWatts() {
        return powerBandMaximumWatts;
    }

    Integer getFlowTemperatureDeciC() {
        return flowTemperatureDeciC;
    }

    List<AssumptionEntity> getAssumptions() {
        return assumptions;
    }

    UUID getValidatedBy() {
        return validatedBy;
    }

    Instant getValidatedAt() {
        return validatedAt;
    }

    LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
