package fr.pacpilot.rulepacks;

/** A source parsed cleanly but must not be published. Always names the file and the cause. */
public class PackValidationException extends RuntimeException {

    public PackValidationException(String message) {
        super(message);
    }
}
