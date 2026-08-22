package fr.pacpilot.server.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The artisan, as an account of record (ADR-0013).
 *
 * <p><b>What this type asserts:</b> this account exists, and work is attributed to it. <b>What it
 * does not assert:</b> that any particular request came from the person behind it. There is no
 * credential here and no session; authentication is M10's. A reader who mistakes a populated
 * {@code validatedBy} for a <i>proven</i> one before M10 has read more into this than it says.
 *
 * <p>That gap is deliberate and narrow — attribution without authentication — and it is why
 * {@code CLAUDE.md} §4.5's legal shield can be persisted honestly from M4 rather than pointing at a
 * stub for four epics.
 */
public record Installer(
        UUID id,
        String displayName,
        Siret siret,
        Optional<String> qualificationRef,
        Instant createdAt,
        Instant updatedAt) {

    public Installer {
        Objects.requireNonNull(id, "an installer account carries its own id");
        Objects.requireNonNull(siret);
        Objects.requireNonNull(qualificationRef);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("an installer account is recorded with a display name");
        }
    }

    /** True once the RGE / QualiPAC reference has been recorded. */
    public boolean isQualificationRecorded() {
        return qualificationRef.isPresent();
    }
}
