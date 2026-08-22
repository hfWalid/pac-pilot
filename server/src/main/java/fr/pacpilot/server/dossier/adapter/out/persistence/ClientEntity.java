package fr.pacpilot.server.dossier.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The {@code dossier_client} row.
 *
 * <p>Package-private, and separate from {@link fr.pacpilot.server.dossier.domain.Client} even
 * though the two currently look alike. They diverge the first time persistence needs something the
 * domain does not have — a version column, a denormalised search field — and merging them now is
 * precisely how JPA reaches the domain. {@code DossierMapper} is the only thing that knows both.
 *
 * <p>No {@code @GeneratedValue}: ids are minted on the installer's device, offline
 * ({@code CLAUDE.md} §4.3).
 *
 * <p>Mutable fields and a no-arg constructor because Hibernate requires them. That requirement is
 * the reason this class exists rather than annotating the record.
 */
@Entity
@Table(name = "dossier_client")
class ClientEntity {

    @Id private UUID id;

    @Column(name = "installer_id", nullable = false)
    private UUID installerId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String email;
    private String phone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "anonymised_at")
    private Instant anonymisedAt;

    protected ClientEntity() {}

    ClientEntity(
            UUID id,
            UUID installerId,
            String firstName,
            String lastName,
            String email,
            String phone,
            Instant createdAt,
            Instant updatedAt,
            Instant anonymisedAt) {
        this.id = id;
        this.installerId = installerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.anonymisedAt = anonymisedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getInstallerId() {
        return installerId;
    }

    String getFirstName() {
        return firstName;
    }

    String getLastName() {
        return lastName;
    }

    String getEmail() {
        return email;
    }

    String getPhone() {
        return phone;
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
