package fr.pacpilot.core.dimensioning.model

/**
 * The observable characteristics of a dwelling that a simplified heat-loss method needs.
 *
 * **Every enumeration here is `TODO(unverified)` on its *members*, not on its existence.**
 * `CLAUDE.md` §6a is explicit that the simplifications which are both field-fast and accepted by
 * QualiPAC audits and decennial insurers are domain knowledge that is *not yet finalized*, and §12
 * forbids shipping an invented coefficient as authoritative. Buckets map to default U-values, so a
 * bucket boundary is a domain fact with the same weight as the coefficient behind it.
 *
 * What is settled: an installer standing in a cellar can observe these things without plans.
 * What is not: where exactly the boundaries fall. That is the M2 ⚑ method-validation gate, and the
 * `FormulaSet` injected there is what attaches numbers to these names. Changing a member later is a
 * migration of stored snapshots, so the M2 gate should confirm these before M4 persists any.
 */

/**
 * Construction period, as a proxy for the thermal regulation in force when the dwelling was built.
 *
 * `BEFORE_1975` is taken from the repository's own worked example in
 * `core/src/commonTest/vectors/README.md`, not chosen here — the remaining boundaries follow the
 * French RT milestones (1974, 1988/89, 2000, 2012) on the same logic.
 *
 * `TODO(unverified)`: confirm at the M2 gate against 3CL-DPE, which is public, before any U-value
 * is attached to a member.
 */
enum class ConstructionPeriod {
    BEFORE_1975,
    FROM_1975_TO_1989,
    FROM_1990_TO_2000,
    FROM_2001_TO_2012,
    AFTER_2012,
}

/**
 * How much insulation work has been done since construction, as the installer can see it.
 *
 * `TODO(unverified)`: a three-way split is a guess at the granularity a 15-minute pre-visit can
 * actually support. Confirm at the M2 gate — and in the field interviews, since an installer who
 * cannot tell PARTIAL from GOOD in a cellar makes the distinction worthless.
 */
enum class InsulationLevel {
    NONE,
    PARTIAL,
    GOOD,
}

/**
 * Ventilation, which sets the air-renewal term of the heat load.
 *
 * `TODO(unverified)`: the hygro-A / hygro-B distinction within simple-flux may matter to the
 * air-change rate and is deliberately not modelled until the M2 gate says whether it does.
 */
enum class VentilationType {
    NATURAL,
    VMC_SIMPLE_FLUX,
    VMC_DOUBLE_FLUX,
}

/**
 * The existing emitters, which constrain the flow temperature and therefore the loi d'eau
 * (`CLAUDE.md` §6a, §13).
 *
 * `TODO(unverified)`: confirm at the M2 gate that these four cover the installed base an artisan
 * meets, and what flow temperature each implies.
 */
enum class EmitterType {
    RADIATOR_HIGH_TEMPERATURE,
    RADIATOR_LOW_TEMPERATURE,
    UNDERFLOOR_HEATING,
    FAN_COIL,
}
