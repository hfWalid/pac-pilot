package fr.pacpilot.server.interventions.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One typed visit on a site — the artisan's operational timeline ({@code CLAUDE.md} §14).
 *
 * <p><b>Not a general agenda, and it must never present itself as one.</b> It holds only what this
 * app owns: visits attached to a {@code Site}. That is precisely what stops it going stale, because
 * nothing external writes to it. Labelled *"mes visites"*, never *"mon agenda"*.
 *
 * <p>Server-side with a client replica, and deliberately not in {@code :core}: it carries no
 * calculation, so it has no reason to live in a module compiled to two targets (DELIVERY-PLAN §3).
 *
 * <p>{@code addressSnapshot} is denormalised at creation so a later {@code Site} correction does not
 * rewrite where a visit was recorded as having happened — the same reasoning that makes
 * {@code InputsSnapshot} copy what it needs rather than point at it.
 */
public record Intervention(
        UUID id,
        UUID siteId,
        InterventionType type,
        InterventionStatus status,
        Instant scheduledAt,
        int durationMinutes,
        String addressSnapshot,
        Optional<java.math.BigDecimal> latitude,
        Optional<java.math.BigDecimal> longitude,
        Optional<String> outcomeNotes,
        /** What a completed pre-visit produced. One-way: the study has no idea a visit exists. */
        Optional<UUID> dimensioningId,
        Optional<UUID> quoteId,
        Optional<String> cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    public Intervention {
        Objects.requireNonNull(id, "an intervention is created with its own id, offline");
        Objects.requireNonNull(siteId, "an intervention happens at a site");
        Objects.requireNonNull(type);
        Objects.requireNonNull(status);
        Objects.requireNonNull(scheduledAt);
        Objects.requireNonNull(latitude);
        Objects.requireNonNull(longitude);
        Objects.requireNonNull(outcomeNotes);
        Objects.requireNonNull(dimensioningId);
        Objects.requireNonNull(quoteId);
        Objects.requireNonNull(cancellationReason);
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("a visit lasts a positive number of minutes");
        }
        if (addressSnapshot == null || addressSnapshot.isBlank()) {
            throw new IllegalArgumentException("an intervention records the address it was booked for");
        }
        // §14: a cancellation or a no-show without a reason tells the artisan nothing they did not
        // already know. Mirrored as a check constraint so it holds for rows written any other way.
        if (status.requiresReason() && cancellationReason.isEmpty()) {
            throw new IllegalArgumentException(status + " must record why");
        }
    }

    /** True once geocoding has succeeded. Never blocking — an installer may be in a cellar. */
    public boolean isGeocoded() {
        return latitude.isPresent() && longitude.isPresent();
    }
}
