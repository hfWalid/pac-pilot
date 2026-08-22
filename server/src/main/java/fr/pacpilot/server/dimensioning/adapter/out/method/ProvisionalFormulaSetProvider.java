package fr.pacpilot.server.dimensioning.adapter.out.method;

import fr.pacpilot.core.dimensioning.engine.ProvisionalFormulaSet;
import fr.pacpilot.core.dimensioning.port.FormulaSet;
import fr.pacpilot.core.dimensioning.port.FormulaSetProvider;
import fr.pacpilot.core.shared.EffectiveDate;

/**
 * Supplies the provisional method for every date.
 *
 * <p>Date-insensitive on purpose, and only defensible <i>because</i> the method is provisional:
 * there is exactly one version and it was never in force, so there is no history to reproduce. A
 * validated provider must honour the date — a study recomputed years later applies the method as it
 * stood when the devis was written ({@code CLAUDE.md} §4.4) — and the parameter is kept in the
 * signature so that provider is a drop-in rather than a change to the port.
 */
final class ProvisionalFormulaSetProvider implements FormulaSetProvider {

    /**
     * The core's own provisional set, not a server-side mirror of it (ADR-0021). One definition, so
     * the tablet and the server cannot drift apart in the third decimal.
     */
    private final FormulaSet provisional = new ProvisionalFormulaSet();

    @Override
    public FormulaSet formulaSetOn(EffectiveDate effectiveDate) {
        return provisional;
    }
}
