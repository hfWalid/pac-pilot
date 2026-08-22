package fr.pacpilot.server.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The {@code identity_installer} row. Package-private; the domain record is the public shape. */
@Entity
@Table(name = "identity_installer")
class InstallerEntity {

    @Id private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String siret;

    @Column(name = "qualification_ref")
    private String qualificationRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InstallerEntity() {}

    InstallerEntity(
            UUID id,
            String displayName,
            String siret,
            String qualificationRef,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.siret = siret;
        this.qualificationRef = qualificationRef;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    String getDisplayName() {
        return displayName;
    }

    String getSiret() {
        return siret;
    }

    String getQualificationRef() {
        return qualificationRef;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
