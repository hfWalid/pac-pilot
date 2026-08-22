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

    /**
     * The same client with their identity severed (ADR-0014).
     *
     * <p>Names are replaced rather than blanked, because the record must survive: a validated study
     * and the devis built on it are the artisan's own evidence, and hard-deleting them on request
     * would destroy the défense the product exists to support. Contact details are removed outright —
     * they carry no evidential weight at all.
     *
     * <p><b>Irreversible by construction.</b> The previous values are not kept anywhere: this returns
     * a new record that never held them, so there is no shadow copy to leak and nothing for a later
     * "undo" to restore. An anonymisation that can be undone is not an erasure.
     */
    public Client anonymised(Instant at) {
        return new Client(
                id, installerId, REDACTED, REDACTED, Optional.empty(), Optional.empty(), createdAt, at,
                Optional.of(at));
    }

    /** What a severed name reads as. Not a real name, and not blank — the record still needs one. */
    public static final String REDACTED = "(efface)";

    private static void requireNamed(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a client is recorded with a " + field);
        }
    }
}
