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

/**
 * One row of {@code quoting_aid_line} — one aid as it was resolved for this devis.
 *
 * <p>The rule id is stored rather than resolved on read. A rule that no longer appears in any
 * current pack is normal and correct: packs are immutable and old ones are never deleted, so the
 * reference stays resolvable long after the barème has moved.
 */
@Entity
@Table(name = "quoting_aid_line")
@IdClass(AidLineEntity.Key.class)
class AidLineEntity {

    @Id
    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    private QuoteEntity quote;

    @Id
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "source", nullable = false)
    private String source;

    protected AidLineEntity() {}

    AidLineEntity(int ordinal, String ruleId, String label, long amountCents, String source) {
        this.ordinal = ordinal;
        this.ruleId = ruleId;
        this.label = label;
        this.amountCents = amountCents;
        this.source = source;
    }

    void attachTo(QuoteEntity owner) {
        this.quote = owner;
    }

    String getRuleId() {
        return ruleId;
    }

    String getLabel() {
        return label;
    }

    long getAmountCents() {
        return amountCents;
    }

    String getSource() {
        return source;
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
