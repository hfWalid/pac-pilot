package fr.pacpilot.server.dossier.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the work happens.
 *
 * <p>The geocode is {@link Optional} and always will be: BAN lookup is opportunistic and never
 * blocks recording a site ({@code CLAUDE.md} §14). An installer standing in a cellar has no
 * network, and a site that could not be saved for want of coordinates would be a site recorded on
 * paper instead.
 *
 * <p>{@code departementCode} is text rather than an integer because 2A, 2B and the overseas 971–976
 * are all real values, and the first integer parse of "2A" is a production incident.
 */
public record SiteAddress(
        String addressLine,
        String postcode,
        String commune,
        String departementCode,
        Optional<BigDecimal> latitude,
        Optional<BigDecimal> longitude) {

    public SiteAddress {
        requirePresent(addressLine, "addressLine");
        requirePresent(postcode, "postcode");
        requirePresent(commune, "commune");
        requirePresent(departementCode, "departementCode");
        Objects.requireNonNull(latitude);
        Objects.requireNonNull(longitude);
    }

    /** True when the BAN lookup has run and produced a position. */
    public boolean isGeocoded() {
        return latitude.isPresent() && longitude.isPresent();
    }

    private static void requirePresent(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a site address needs a " + field);
        }
    }
}
