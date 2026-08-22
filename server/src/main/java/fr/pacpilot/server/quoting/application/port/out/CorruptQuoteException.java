package fr.pacpilot.server.quoting.application.port.out;

/**
 * A stored devis cannot be rebuilt into a valid aggregate.
 *
 * <p>Its own type, extending {@link RuntimeException} directly, for a reason discovered by test:
 * a {@code @Repository} is wrapped by Spring's persistence-exception translation, which maps
 * {@link IllegalStateException} and {@link IllegalArgumentException} onto
 * {@code InvalidDataAccessApiUsageException}. A devis referencing an unsigned study would therefore
 * have surfaced as an <i>API misuse</i> — the caller passed something wrong — when it is nothing of
 * the kind: the call was correct and the data underneath it is not. Anything not in Spring's map
 * passes through untranslated, so this arrives at the caller intact.
 *
 * <p>The distinction matters beyond tidiness. This exception means the audit chain is broken for one
 * document, which is an operational incident to investigate, not a bad request to reject — and at
 * M8 it is the difference between an anomaly flag and a 400.
 */
public class CorruptQuoteException extends RuntimeException {

    public CorruptQuoteException(String message) {
        super(message);
    }
}
