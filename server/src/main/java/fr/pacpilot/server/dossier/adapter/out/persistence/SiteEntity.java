package fr.pacpilot.server.dossier.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code dossier_site} row. Package-private; see {@link ClientEntity} for why it is not the
 * domain record.
 *
 * <p>The dwelling characteristics are stored in the integer units the core's value objects hold —
 * centi-square-metres, centimetres — so no rounding happens at this boundary. The categorical
 * values are the enum names as text, matched by a check constraint in the migration rather than a
 * Postgres enum type.
 */
@Entity
@Table(name = "dossier_site")
class SiteEntity {

    @Id private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String postcode;

    @Column(nullable = false)
    private String commune;

    @Column(name = "departement_code", nullable = false)
    private String departementCode;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "surface_centi_m2", nullable = false)
    private int surfaceCentiSquareMetres;

    @Column(name = "ceiling_height_cm", nullable = false)
    private int ceilingHeightCentimetres;

    @Column(name = "construction_period", nullable = false)
    private String constructionPeriod;

    @Column(name = "insulation_level", nullable = false)
    private String insulationLevel;

    @Column(name = "ventilation_type", nullable = false)
    private String ventilationType;

    @Column(name = "emitter_type", nullable = false)
    private String emitterType;

    @Column(name = "electrical_supply_kva", nullable = false)
    private int electricalSupplyKva;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "anonymised_at")
    private Instant anonymisedAt;

    protected SiteEntity() {}

    @SuppressWarnings("checkstyle:ParameterNumber")
    SiteEntity(
            UUID id,
            UUID clientId,
            String addressLine,
            String postcode,
            String commune,
            String departementCode,
            BigDecimal latitude,
            BigDecimal longitude,
            int surfaceCentiSquareMetres,
            int ceilingHeightCentimetres,
            String constructionPeriod,
            String insulationLevel,
            String ventilationType,
            String emitterType,
            int electricalSupplyKva,
            Instant createdAt,
            Instant updatedAt,
            Instant anonymisedAt) {
        this.id = id;
        this.clientId = clientId;
        this.addressLine = addressLine;
        this.postcode = postcode;
        this.commune = commune;
        this.departementCode = departementCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.surfaceCentiSquareMetres = surfaceCentiSquareMetres;
        this.ceilingHeightCentimetres = ceilingHeightCentimetres;
        this.constructionPeriod = constructionPeriod;
        this.insulationLevel = insulationLevel;
        this.ventilationType = ventilationType;
        this.emitterType = emitterType;
        this.electricalSupplyKva = electricalSupplyKva;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.anonymisedAt = anonymisedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getClientId() {
        return clientId;
    }

    String getAddressLine() {
        return addressLine;
    }

    String getPostcode() {
        return postcode;
    }

    String getCommune() {
        return commune;
    }

    String getDepartementCode() {
        return departementCode;
    }

    BigDecimal getLatitude() {
        return latitude;
    }

    BigDecimal getLongitude() {
        return longitude;
    }

    int getSurfaceCentiSquareMetres() {
        return surfaceCentiSquareMetres;
    }

    int getCeilingHeightCentimetres() {
        return ceilingHeightCentimetres;
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

    int getElectricalSupplyKva() {
        return electricalSupplyKva;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getAnonymisedAt() {
        return anonymisedAt;
    }
}
