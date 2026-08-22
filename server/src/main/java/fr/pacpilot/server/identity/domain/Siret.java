package fr.pacpilot.server.identity.domain;

/**
 * A French business identifier: fourteen digits, exactly.
 *
 * <p>A type rather than a {@code String} field because the invariant has to live somewhere, and the
 * alternative is the same regex repeated at every boundary until one of them is missed.
 *
 * <p>Held as text, never as a number. Leading zeros are significant, no arithmetic is ever done on
 * it, and the first time one is parsed into a {@code long} the leading zero is gone for good.
 */
public record Siret(String value) {

    private static final int LENGTH = 14;

    public Siret {
        if (value == null || !value.matches("[0-9]{" + LENGTH + "}")) {
            // The value is not echoed: a malformed SIRET is still a business identifier, and error
            // messages travel further than the request that produced them.
            throw new IllegalArgumentException("a SIRET is exactly " + LENGTH + " digits");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
