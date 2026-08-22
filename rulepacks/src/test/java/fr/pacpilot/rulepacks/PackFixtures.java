package fr.pacpilot.rulepacks;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Test fixtures.
 *
 * <p><b>Every source here is synthetic and says so.</b> Real barème values arrive only at the ⚑ gate
 * (PAC-75), verified by a human against the official sources — an agent can encode what it is told
 * and check arithmetic, but it cannot decide that a plafond is right. Round, obviously-invented
 * numbers, in the same spirit as the M3 sample packs.
 */
final class PackFixtures {

    private PackFixtures() {}

    /** A fresh key per test run. Nothing key-shaped is ever committed. */
    static String aSigningKey() {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    static String firstHalf() {
        return """
                version        = test-2025-H1
                effective-from = 2025-01-01
                effective-to   = 2025-06-30

                [vat]
                rate   = 10.00
                source = FIXTURE — synthetic, not a real rate

                [aid income-tiered]
                id       = fixture-tiered
                label    = Aide indexee (fixture)
                source   = FIXTURE — synthetic, consulte le 2026-08-22
                decile.1 = 1000.00
                decile.2 = 2000.00
                decile.3 = 3000.00

                [aid forfait]
                id     = fixture-forfait
                label  = Forfait (fixture)
                source = FIXTURE — synthetic
                amount = 500.00

                [aid rate-based]
                id     = fixture-rate
                label  = Taux plafonne (fixture)
                source = FIXTURE — synthetic
                rate   = 50.00
                cap    = 2000.00
                """;
    }

    /** Starts the day after {@link #firstHalf} ends — effectiveTo is inclusive (M1-07). */
    static String secondHalf() {
        return firstHalf()
                .replace("version        = test-2025-H1", "version        = test-2025-H2")
                .replace("effective-from = 2025-01-01", "effective-from = 2025-07-01")
                .replace("effective-to   = 2025-06-30", "effective-to   = 2025-12-31");
    }
}
