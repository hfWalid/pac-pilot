package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRulePack;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The serialised form of a <i>published</i> pack — the encoded source plus the checksum and
 * signature the pipeline added.
 *
 * <p>Deliberately the source format with a header rather than a new one: the artefact a device pulls
 * should be readable by the same person who wrote the source, so that verifying what was published
 * against what was written is reading two similar files rather than decoding one.
 */
final class PublishedPackFormat {

    private static final String CHECKSUM = "checksum";
    private static final String SIGNATURE = "signature";

    private PublishedPackFormat() {}

    /**
     * @param sourceText the original encoded source, verbatim. Not the canonical checksum rendering:
     *     that form is for hashing and is deliberately unreadable, while this artefact has to stay
     *     legible to whoever verifies it against the barème.
     */
    static String write(String sourceText, String checksum, String signature) {
        return "# Published pack — do not edit. Immutable once published (CLAUDE.md §4.4).\n"
                + CHECKSUM
                + " = "
                + checksum
                + "\n"
                + SIGNATURE
                + " = "
                + signature
                + "\n"
                + "\n"
                + sourceText;
    }

    /**
     * Reads a published pack back.
     *
     * <p>Used by the pipeline to see the existing series before validating a successor, and by the
     * server adapter to resolve one. It re-parses the source body rather than trusting a summary,
     * so what is read is what was written.
     */
    static AidRulePack read(String content, String origin) {
        Map<String, String> header =
                content
                        .lines()
                        .map(line -> line.contains("#") ? line.substring(0, line.indexOf('#')) : line)
                        .map(String::trim)
                        .filter(line -> line.startsWith(CHECKSUM + " ") || line.startsWith(SIGNATURE + " "))
                        .collect(
                                Collectors.toMap(
                                        line -> line.substring(0, line.indexOf('=')).trim(),
                                        line -> line.substring(line.indexOf('=') + 1).trim()));

        String checksum = header.get(CHECKSUM);
        String signature = header.get(SIGNATURE);
        if (checksum == null || signature == null) {
            throw new PackSourceException(origin, 0, "a published pack carries a checksum and a signature");
        }

        String body =
                content
                        .lines()
                        .filter(line -> !line.trim().startsWith(CHECKSUM + " ") && !line.trim().startsWith(SIGNATURE + " "))
                        .collect(Collectors.joining("\n"));

        return PackSourceParser.parse(body, origin).intoPack(checksum, signature);
    }
}
