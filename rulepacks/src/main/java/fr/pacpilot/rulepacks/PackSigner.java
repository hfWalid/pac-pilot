package fr.pacpilot.rulepacks;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Signs a pack's checksum, so a device can tell a published pack from one somebody made.
 *
 * <p><b>Ed25519, not an HMAC.</b> Packs are distributed over a CDN to devices we do not control, so
 * verification has to be possible with a <i>public</i> key shipped in the app. A shared secret would
 * mean every installer's device carried the key that could forge a barème.
 *
 * <p><b>The private key never enters the repository.</b> It is read from
 * {@code PACPILOT_RULEPACK_SIGNING_KEY} — base64 PKCS#8 — and a missing key stops the pipeline rather
 * than quietly producing an unsigned pack. An unsigned pack that reached a device would defeat the
 * whole mechanism silently, which is why this fails loudly instead.
 */
final class PackSigner {

    static final String KEY_VARIABLE = "PACPILOT_RULEPACK_SIGNING_KEY";

    private final PrivateKey privateKey;

    private PackSigner(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * @throws IllegalStateException when the key is absent or unreadable. Publishing without a key is
     *     not a degraded mode; it is a stop.
     */
    static PackSigner fromEnvironment(String base64PrivateKey) {
        if (base64PrivateKey == null || base64PrivateKey.isBlank()) {
            throw new IllegalStateException(
                    KEY_VARIABLE
                            + " is not set. The pipeline will not publish an unsigned pack: a device"
                            + " cannot tell an unsigned pack from a forged one. Set the key from the"
                            + " deployment's secret store — never from a file in this repository.");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(base64PrivateKey.trim());
            return new PackSigner(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded)));
        } catch (Exception failure) {
            // Deliberately does not echo the value: a malformed key is still key material.
            throw new IllegalStateException(
                    KEY_VARIABLE + " is set but is not a base64 PKCS#8 Ed25519 private key", failure);
        }
    }

    String sign(String checksum) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(checksum.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception failure) {
            throw new IllegalStateException("could not sign the pack checksum", failure);
        }
    }
}
