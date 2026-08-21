package fr.pacpilot.core.dimensioning.port

import fr.pacpilot.core.shared.EffectiveDate

/**
 * The coefficients and formulas a dimensioning run applies, as one versioned, injected unit.
 *
 * **Declares no members, and that is deliberate.** Every member it will eventually carry is a
 * coefficient accessor — a default U-value for a construction period, a base temperature for a
 * zone, an air-change rate for a ventilation type. `CLAUDE.md` §12 forbids shipping an invented
 * coefficient as authoritative, and naming the accessors now would commit to the shape of a method
 * the M2 ⚑ gate has not yet validated. The boundary is what M1 owes; the contents are M2's.
 */
interface FormulaSet

/**
 * Driven port — supplies the formula set in force on a given date.
 *
 * Date-parameterised for the same reason the rule packs are (`CLAUDE.md` §4.4): a study recomputed
 * years later must apply the method as it stood when the devis was written, not the current one.
 * Without the parameter the core would have to ask "what is the method now", which is a clock.
 */
interface FormulaSetProvider {

    fun formulaSetOn(effectiveDate: EffectiveDate): FormulaSet
}
