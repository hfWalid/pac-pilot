package fr.pacpilot.server.quoting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** One row of {@code quoting_line_item}. Carries its own VAT rate, per M1-08. */
@Entity
@Table(name = "quoting_line_item")
@IdClass(LineItemEntity.Key.class)
class LineItemEntity {

    @Id
    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    private QuoteEntity quote;

    @Id
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "vat_basis_points", nullable = false)
    private int vatBasisPoints;

    protected LineItemEntity() {}

    LineItemEntity(int ordinal, String label, long unitPriceCents, int quantity, int vatBasisPoints) {
        this.ordinal = ordinal;
        this.label = label;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
        this.vatBasisPoints = vatBasisPoints;
    }

    void attachTo(QuoteEntity owner) {
        this.quote = owner;
    }

    String getLabel() {
        return label;
    }

    long getUnitPriceCents() {
        return unitPriceCents;
    }

    int getQuantity() {
        return quantity;
    }

    int getVatBasisPoints() {
        return vatBasisPoints;
    }

    static class Key implements Serializable {
        private UUID quote;
        private int ordinal;

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && key.ordinal == ordinal && Objects.equals(key.quote, quote);
        }

        @Override
        public int hashCode() {
            return Objects.hash(quote, ordinal);
        }
    }
}
