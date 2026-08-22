package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRulePackCanonicalForm;
import fr.pacpilot.core.aids.model.AidRulePackPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 over the pack's canonical rendering.
 *
 * <p><b>The rendering itself lives in {@code :core}</b>, in {@code AidRulePackCanonicalForm}, because
 * the device verifies the same checksum this pipeline produces ({@code CLAUDE.md} §7). Two
 * implementations of "what exactly is hashed" would eventually disagree, and the failure would look
 * like tampering rather than like a bug.
 *
 * <p>What stays here is the hash, which is a platform primitive — the same split M1 made for
 * signatures: the domain describes, the platform computes.
 */
final class PackChecksum {

    private PackChecksum() {}

    static String of(PackSource source) {
        return sha256(
                AidRulePackCanonicalForm.INSTANCE.of(
                        source.version(),
                        source.effectiveFrom(),
                        source.effectiveTo().orElse(null),
                        source.payload()));
    }

    static String sha256(String canonical) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
