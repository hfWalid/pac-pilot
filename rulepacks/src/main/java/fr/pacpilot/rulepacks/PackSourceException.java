package fr.pacpilot.rulepacks;

/**
 * A source file could not be read.
 *
 * <p>Always names the file and, where one applies, the line. These messages are read by someone
 * mid-publication with a barème page open — "validation failed" sends them to the code, "sources/
 * 2026-H1.pack line 14: [aid forfait] is missing 'source'" sends them to the fix.
 */
public class PackSourceException extends RuntimeException {

    public PackSourceException(String origin, int line, String problem) {
        super(line > 0 ? origin + " line " + line + ": " + problem : origin + ": " + problem);
    }
}
