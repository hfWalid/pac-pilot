package fr.pacpilot.server.dimensioning.adapter.out.method;

/**
 * Which version of the simplified heat-loss method this deployment runs.
 *
 * <p>Bound to {@code pacpilot.dimensioning.method}, which has <b>no default</b> (ADR-0015). A server
 * started without it fails at context refresh rather than quietly picking one. That is the whole
 * mechanism: indicative mode is reached only by someone who wrote the word.
 */
public enum DimensioningMethod {

    /**
     * No validated method is in force. Every coefficient is {@code SOURCE_TBD} and deliberately
     * non-physical, so every result reports {@code INDICATIVE} confidence and nothing computed may
     * be shown to a client.
     *
     * <p>Exists because {@link fr.pacpilot.core.dimensioning.port.FormulaSet} has no validated
     * implementation while PAC-42 is open, and M4 still has to prove the pre-visit flow end to end.
     */
    INDICATIVE_PROVISIONAL,

    /**
     * The method validated at the PAC-42 gate.
     *
     * <p>Declared but not yet selectable: choosing it fails at startup with a message pointing at
     * the gate. Named now so the property that operators will eventually set already exists, and so
     * the failure for choosing it is a clear one rather than an unknown-enum-value stack trace.
     */
    VALIDATED
}
