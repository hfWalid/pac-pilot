package fr.pacpilot.server.dossier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A dwelling belonging to a {@link Client}, and the thing a pre-visit is about.
 *
 * <p>Server-side (DELIVERY-PLAN §3). Its id is client-generated offline, like every aggregate here.
 *
 * <p><b>A site is mutable and that is the point of the snapshot rule.</b> Insulation is improved,
 * emitters are replaced, a surface is corrected. Every one of those is a legitimate edit, and none
 * of them may reach backwards into a dimensioning that was validated before it. The protection is
 * that {@code InputsSnapshot} copies, and nothing in this context hands out a live reference that
 * a study could hold instead.
 */
public record Site(
        UUID id,
        UUID clientId,
        SiteAddress address,
        DwellingObservations observations,
        Instant createdAt,
        Instant updatedAt,
        Optional<Instant> anonymisedAt) {

    public Site {
        Objects.requireNonNull(id, "a site is created with its own id, offline");
        Objects.requireNonNull(clientId, "a site belongs to a client");
        Objects.requireNonNull(address);
        Objects.requireNonNull(observations);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    /** A site with corrected observations. Returns a new instance; nothing mutates in place. */
    public Site observing(DwellingObservations corrected, Instant at) {
        return new Site(id, clientId, address, corrected, createdAt, at, anonymisedAt);
    }
}
