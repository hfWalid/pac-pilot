package fr.pacpilot.server.dimensioning.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The {@code dimensioning_verification} row. */
@Entity
@Table(name = "dimensioning_verification")
class VerificationEntity {

    @Id private UUID id;

    @Column(name = "study_id", nullable = false)
    private UUID studyId;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "outcome", nullable = false)
    private String outcome;

    @Column(name = "differences")
    private String differences;

    @Column(name = "reason")
    private String reason;

    protected VerificationEntity() {}

    VerificationEntity(
            UUID id, UUID studyId, Instant verifiedAt, String outcome, String differences, String reason) {
        this.id = id;
        this.studyId = studyId;
        this.verifiedAt = verifiedAt;
        this.outcome = outcome;
        this.differences = differences;
        this.reason = reason;
    }

    UUID getId() {
        return id;
    }

    UUID getStudyId() {
        return studyId;
    }

    Instant getVerifiedAt() {
        return verifiedAt;
    }

    String getOutcome() {
        return outcome;
    }

    String getDifferences() {
        return differences;
    }

    String getReason() {
        return reason;
    }
}
