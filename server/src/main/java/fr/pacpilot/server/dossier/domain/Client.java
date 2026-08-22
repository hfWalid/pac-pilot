package fr.pacpilot.server.dossier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The homeowner a devis is addressed to, and the aggregate carrying the most personal data in the
 * product (ADR-0014).
 *
 * <p>Server-side and outside the KMP core (DELIVERY-PLAN §3): a client carries no calculation, so
 * there is nothing for the JS target to do with one.
 *
 * <p>Contact details are {@link Optional} because ADR-0014 hard-deletes them on erasure while the
 * client row survives, de-linked, alongside studies that remain evidence. A client whose email is
 * gone is a normal state, not a broken row, and modelling it as nullable-by-accident would leave
 * every reader guessing which it was.
 *
 * @param id client-generated at creation, offline ({@code CLAUDE.md} §4.3)
 */
public record Client(
        UUID id,
        UUID installerId,
        String firstName,
        String lastName,
        Optional<String> email,
        Optional<String> phone,
        Instant createdAt,
        Instant updatedAt,
        Optional<Instant> anonymisedAt) {

    public Client {
        Objects.requireNonNull(id, "a client is created with its own id, offline");
        Objects.requireNonNull(installerId, "a client belongs to the installer who recorded them");
        requireNamed(firstName, "firstName");
        requireNamed(lastName, "lastName");
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    /** True once erasure has been exercised; the identity is severed but the record survives. */
    public boolean isAnonymised() {
        return anonymisedAt.isPresent();
    }

    private static void requireNamed(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a client is recorded with a " + field);
        }
    }
}
