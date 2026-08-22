package fr.pacpilot.server.interventions.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The {@code interventions_intervention} row. Package-private. */
@Entity
@Table(name = "interventions_intervention")
class InterventionEntity {

    @Id private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Denormalised personal data. PAC-60's erasure sweep reaches it. */
    @Column(name = "address_snapshot", nullable = false)
    private String addressSnapshot;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "outcome_notes")
    private String outcomeNotes;

    @Column(name = "dimensioning_id")
    private UUID dimensioningId;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InterventionEntity() {}

    @SuppressWarnings("checkstyle:ParameterNumber")
    InterventionEntity(
            UUID id,
            UUID siteId,
            String type,
            String status,
            Instant scheduledAt,
            int durationMinutes,
            String addressSnapshot,
            BigDecimal latitude,
            BigDecimal longitude,
            String outcomeNotes,
            UUID dimensioningId,
            UUID quoteId,
            String cancellationReason,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.siteId = siteId;
        this.type = type;
        this.status = status;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
        this.addressSnapshot = addressSnapshot;
        this.latitude = latitude;
        this.longitude = longitude;
        this.outcomeNotes = outcomeNotes;
        this.dimensioningId = dimensioningId;
        this.quoteId = quoteId;
        this.cancellationReason = cancellationReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getSiteId() {
        return siteId;
    }

    String getType() {
        return type;
    }

    String getStatus() {
        return status;
    }

    Instant getScheduledAt() {
        return scheduledAt;
    }

    int getDurationMinutes() {
        return durationMinutes;
    }

    String getAddressSnapshot() {
        return addressSnapshot;
    }

    BigDecimal getLatitude() {
        return latitude;
    }

    BigDecimal getLongitude() {
        return longitude;
    }

    String getOutcomeNotes() {
        return outcomeNotes;
    }

    UUID getDimensioningId() {
        return dimensioningId;
    }

    UUID getQuoteId() {
        return quoteId;
    }

    String getCancellationReason() {
        return cancellationReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
