package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRulePack;
import fr.pacpilot.core.aids.model.AidRulePackFormat;
import fr.pacpilot.core.aids.model.AidRulePackFormatException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Encoded source in, published pack out: build → validate → checksum → sign → publish.
 *
 * <p><b>Runnable from the command line, not only from a build task</b>, because the runbook (PAC-74)
 * depends on it and because publishing is a deliberate act. Nothing here is wired into {@code check}
 * or {@code build}: a merge must not be able to publish a barème.
 *
 * <pre>{@code
 * PACPILOT_RULEPACK_SIGNING_KEY=... \
 *   ./gradlew :rulepacks:run --args="rulepacks/sources/2026-H1.pack rulepacks/published"
 * }</pre>
 */
public final class PackPipeline {

    private final PackStore store;
    private final PackSigner signer;

    public PackPipeline(PackStore store, PackSigner signer) {
        this.store = store;
        this.signer = signer;
    }

    /**
     * Publishes one source.
     *
     * @return the published pack, so a caller can report what happened rather than assume.
     */
    public AidRulePack publish(String sourceText, String origin) {
        PackSource source = PackSource.read(sourceText, origin);

        List<AidRulePack> alreadyPublished = store.published();
        PackValidator.validate(source, alreadyPublished);

        // The checksum covers the canonical content, never the file's formatting — so reflowing a
        // source without changing a value produces the same checksum, and changing any value does
        // not.
        String checksum = PackChecksum.of(source);
        String signature = signer.sign(checksum);

        AidRulePack pack = source.intoPack(checksum, signature);
        store.publish(pack, AidRulePackFormat.INSTANCE.writePublished(sourceText, checksum, signature));
        return pack;
    }

    /** Builds and validates without publishing — what a reviewer runs before the ⚑ gate. */
    public static PackSource dryRun(String sourceText, String origin, List<AidRulePack> alreadyPublished) {
        PackSource source = PackSource.read(sourceText, origin);
        PackValidator.validate(source, alreadyPublished);
        return source;
    }

    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            System.err.println("usage: <source.pack> <published-directory>");
            System.exit(2);
            return;
        }

        Path sourceFile = Path.of(arguments[0]);
        PackStore store = new FilePackStore(Path.of(arguments[1]));

        try {
            String sourceText = Files.readString(sourceFile, StandardCharsets.UTF_8);
            PackSigner signer = PackSigner.fromEnvironment(System.getenv(PackSigner.KEY_VARIABLE));
            AidRulePack published = new PackPipeline(store, signer).publish(sourceText, sourceFile.toString());

            System.out.println("published " + published.getVersion().getValue());
            System.out.println("  range    " + published.getEffectiveRange());
            System.out.println("  checksum " + published.getChecksum());
            System.out.println("  rules    " + published.getPayload().getAids().size());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (AidRulePackFormatException | PackValidationException | IllegalStateException refused) {
            // The message is the product here — it is read mid-publication with a barème page open.
            System.err.println("REFUSED: " + refused.getMessage());
            System.exit(1);
        }
    }
}
