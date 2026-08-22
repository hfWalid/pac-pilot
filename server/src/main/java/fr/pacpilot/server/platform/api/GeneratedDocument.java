package fr.pacpilot.server.platform.api;

import java.util.Arrays;
import java.util.Objects;

/**
 * A generated document: its bytes and what they are.
 *
 * <p><b>Bytes, not a path and not a stream to somewhere.</b> Generating a document and deciding
 * where it lives are different responsibilities — object storage is M9's — and a port that returned
 * a location would have made that decision on M9's behalf.
 *
 * <p>Lives in {@code platform.api} because both Quoting and Dimensioning produce one and neither
 * owns the concept. The {@code .api} placement is not cosmetic: {@code BoundedContextRulesTest}
 * caught it in {@code platform.document} on the first build and refused it, because a context
 * reaching into another context's internals is the wall this product is built on. {@code platform}
 * may see other contexts; they may only see its published surface.
 *
 * <p>It carries no library type, which is what lets the same port be satisfied by a headless browser
 * or by a pure-JVM library.
 */
public record GeneratedDocument(byte[] content, String contentType) {

    public static final String PDF = "application/pdf";

    public GeneratedDocument {
        Objects.requireNonNull(content, "a generated document has content");
        if (content.length == 0) {
            throw new IllegalArgumentException("an empty document is a failure, not a document");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("a generated document declares what it is");
        }
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    /** Value equality on the bytes — this is what M5-06's byte-identity assertions compare. */
    @Override
    public boolean equals(Object other) {
        return other instanceof GeneratedDocument document
                && contentType.equals(document.contentType)
                && Arrays.equals(content, document.content);
    }

    @Override
    public int hashCode() {
        return 31 * contentType.hashCode() + Arrays.hashCode(content);
    }

    /** Deliberately does not dump the bytes. */
    @Override
    public String toString() {
        return "GeneratedDocument(" + contentType + ", " + content.length + " bytes)";
    }
}
