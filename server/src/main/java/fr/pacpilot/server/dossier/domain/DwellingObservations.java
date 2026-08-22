package fr.pacpilot.server.dossier.domain;

import java.util.Objects;

/**
 * What the installer read off the building, in the exact integer units the core's value objects
 * hold — so the mapping in and out is lossless and no rounding happens at a boundary.
 *
 * <p><b>Observations, not a study.</b> These are the site as it stands today. A dimensioning copies
 * them into its own snapshot at the moment it runs, and that copy is what a validated study is
 * defended on. Editing a site next year must not change what a past study appears to have been
 * computed from, which is why this type is reachable from {@link Site} and from nothing else.
 *
 * <p>The four categorical values are held as their core enum *names* rather than as the enums
 * themselves, keeping the dossier domain free of dimensioning vocabulary. The mapper resolves them.
 */
public record DwellingObservations(
        int surfaceCentiSquareMetres,
        int ceilingHeightCentimetres,
        String constructionPeriod,
        String insulationLevel,
        String ventilationType,
        String emitterType,
        int electricalSupplyKva) {

    public DwellingObservations {
        requirePositive(surfaceCentiSquareMetres, "a heated surface");
        requirePositive(ceilingHeightCentimetres, "a ceiling height");
        requirePositive(electricalSupplyKva, "an electrical supply");
        Objects.requireNonNull(constructionPeriod);
        Objects.requireNonNull(insulationLevel);
        Objects.requireNonNull(ventilationType);
        Objects.requireNonNull(emitterType);
    }

    private static void requirePositive(int value, String what) {
        if (value <= 0) {
            throw new IllegalArgumentException(what + " is strictly positive, was " + value);
        }
    }
}
