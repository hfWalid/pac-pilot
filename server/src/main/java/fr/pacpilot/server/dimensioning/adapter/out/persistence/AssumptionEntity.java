package fr.pacpilot.server.dimensioning.adapter.out.persistence;

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
 * One row of {@code dimensioning_assumption} — one thing the method assumed, and its source.
 *
 * <p>Rows rather than a serialised blob, decided at PAC-54. The first question that will be asked of
 * this data is already known: when the M2 gate closes, someone has to find every study computed
 * under the provisional method. Against rows that is a {@code WHERE source LIKE 'SOURCE_TBD%'};
 * against a blob it is a full deserialisation sweep.
 *
 * <p>{@code ordinal} is part of the key because the log is ordered — it is the order the method made
 * its assumptions in, and it is what an auditor reads down.
 */
@Entity
@Table(name = "dimensioning_assumption")
@IdClass(AssumptionEntity.Key.class)
class AssumptionEntity {

    @Id
    @ManyToOne
    @JoinColumn(name = "study_id", nullable = false)
    private DimensioningStudyEntity study;

    @Id
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "statement", nullable = false)
    private String statement;

    @Column(name = "source", nullable = false)
    private String source;

    protected AssumptionEntity() {}

    AssumptionEntity(int ordinal, String statement, String source) {
        this.ordinal = ordinal;
        this.statement = statement;
        this.source = source;
    }

    void attachTo(DimensioningStudyEntity owner) {
        this.study = owner;
    }

    int getOrdinal() {
        return ordinal;
    }

    String getStatement() {
        return statement;
    }

    String getSource() {
        return source;
    }

    /** Composite key: the study it belongs to, and its position in the log. */
    static class Key implements Serializable {
        private UUID study;
        private int ordinal;

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && key.ordinal == ordinal
                    && Objects.equals(key.study, study);
        }

        @Override
        public int hashCode() {
            return Objects.hash(study, ordinal);
        }
    }
}
