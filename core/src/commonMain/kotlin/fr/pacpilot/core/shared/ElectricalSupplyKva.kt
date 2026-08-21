package fr.pacpilot.core.shared

/**
 * The dwelling's subscribed electrical supply — the *puissance souscrite* on the meter.
 *
 * Named in `CLAUDE.md` §3 as part of the Site record and required by the dimensioning inputs. It
 * constrains which machines can be installed at all: a heat pump whose start-up draw exceeds what
 * the supply allows means the homeowner must upgrade their contract, which is a cost that belongs
 * on the devis rather than a surprise in February.
 *
 * **kVA, not kW, and therefore not [PowerKw].** French domestic supply is rated in apparent power
 * and sold in fixed steps; conflating it with the thermal kilowatts of a heat load is exactly the
 * primitive-obsession error the unit types exist to prevent.
 *
 * Whole kVA: the standard subscription steps are integers and nothing here needs finer.
 */
data class ElectricalSupplyKva(val kva: Int) {

    init {
        require(kva > 0) { "a subscribed supply is strictly positive, was $kva kVA" }
    }

    fun render(): String = kva.toString()

    override fun toString(): String = render() + " " + SYMBOL

    companion object {
        const val SYMBOL: String = "kVA"
    }
}
