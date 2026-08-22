package fr.pacpilot.server.quoting.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The {@code quoting_quote} row.
 *
 * <p>Carries no total of any kind, matching the schema and the domain. {@code Quote} derives its
 * subtotal, VAT, TTC and reste-à-charge from its lines every time they are read; a field here would
 * be the second source of truth that PAC-55 names as the thing under pressure in this ticket.
 */
@Entity
@Table(name = "quoting_quote")
class QuoteEntity {

    @Id private UUID id;

    @Column(name = "dimensioning_id", nullable = false)
    private UUID dimensioningId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "product_model", nullable = false)
    private String productModel;

    @Column(name = "product_power_at_m7_w", nullable = false)
    private int productPowerAtMinusSevenWatts;

    @Column(name = "product_price_cents", nullable = false)
    private long productPriceCents;

    @Column(name = "aid_pack_version", nullable = false)
    private String aidPackVersion;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "quote",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<LineItemEntity> lines = new ArrayList<>();

    @OneToMany(
            mappedBy = "quote",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("ordinal ASC")
    private List<AidLineEntity> aidLines = new ArrayList<>();

    protected QuoteEntity() {}

    @SuppressWarnings("checkstyle:ParameterNumber")
    QuoteEntity(
            UUID id,
            UUID dimensioningId,
            String productId,
            String productModel,
            int productPowerAtMinusSevenWatts,
            long productPriceCents,
            String aidPackVersion,
            LocalDate effectiveDate,
            String status,
            Instant createdAt) {
        this.id = id;
        this.dimensioningId = dimensioningId;
        this.productId = productId;
        this.productModel = productModel;
        this.productPowerAtMinusSevenWatts = productPowerAtMinusSevenWatts;
        this.productPriceCents = productPriceCents;
        this.aidPackVersion = aidPackVersion;
        this.effectiveDate = effectiveDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    void replaceLines(List<LineItemEntity> replacements) {
        lines.clear();
        replacements.forEach(
                line -> {
                    line.attachTo(this);
                    lines.add(line);
                });
    }

    void replaceAidLines(List<AidLineEntity> replacements) {
        aidLines.clear();
        replacements.forEach(
                line -> {
                    line.attachTo(this);
                    aidLines.add(line);
                });
    }

    UUID getId() {
        return id;
    }

    UUID getDimensioningId() {
        return dimensioningId;
    }

    String getProductId() {
        return productId;
    }

    String getProductModel() {
        return productModel;
    }

    int getProductPowerAtMinusSevenWatts() {
        return productPowerAtMinusSevenWatts;
    }

    long getProductPriceCents() {
        return productPriceCents;
    }

    String getAidPackVersion() {
        return aidPackVersion;
    }

    LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    String getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    List<LineItemEntity> getLines() {
        return lines;
    }

    List<AidLineEntity> getAidLines() {
        return aidLines;
    }
}
