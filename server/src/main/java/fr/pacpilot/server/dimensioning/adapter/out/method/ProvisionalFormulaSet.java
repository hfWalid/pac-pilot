package fr.pacpilot.server.dimensioning.adapter.out.method;

import fr.pacpilot.core.dimensioning.model.ConstructionPeriod;
import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.dimensioning.model.InsulationLevel;
import fr.pacpilot.core.dimensioning.model.ValidatedEnvelope;
import fr.pacpilot.core.dimensioning.model.VentilationType;
import fr.pacpilot.core.dimensioning.port.FlowTemperatureGuidance;
import fr.pacpilot.core.dimensioning.port.FormulaSet;
import fr.pacpilot.core.dimensioning.port.Sourced;
import fr.pacpilot.core.shared.AirChangeRate;
import fr.pacpilot.core.shared.CeilingHeightM;
import fr.pacpilot.core.shared.EnvelopeAreaFactor;
import fr.pacpilot.core.shared.Percentage;
import fr.pacpilot.core.shared.SurfaceM2;
import fr.pacpilot.core.shared.TemperatureC;
import fr.pacpilot.core.shared.ThermalTransmittance;
import fr.pacpilot.core.shared.VolumetricHeatCapacity;
import java.util.Set;

/**
 * A formula set with no validated content whatsoever, so the server can run the pre-visit flow while
 * the PAC-42 ⚑ gate is open (ADR-0015).
 *
 * <p><b>Every value here is deliberately wrong, and wrong in a way that is obvious.</b> The
 * U-values <i>rise</i> with newer construction, which is backwards. The air-change rate <i>rises</i>
 * with better ventilation, also backwards. Air's volumetric heat capacity is 1.000 rather than
 * roughly 0.34, and the envelope-area factor is unity, which no dwelling achieves. A reviewer
 * glancing at a heat load computed from these cannot mistake it for a study. That is the protection:
 * not that the numbers are hidden, but that they are visibly not a method.
 *
 * <p><b>Why this exists in Java, mirroring a Kotlin fixture.</b> {@code :core}'s
 * {@code ProvisionalFormulaSet} lives in {@code commonTest}, where M2-07 put it so that shipping it
 * was impossible by construction. ADR-0015 overruled the consequence — the server boots rather than
 * refusing — but not the placement: the core keeps no shippable placeholder, so the server carries
 * its own. The two must produce identical results, and
 * {@code ProvisionalMethodMatchesCoreGoldenVectorsTest} asserts exactly that against published
 * golden vectors rather than trusting that the values were copied correctly.
 *
 * <p><b>This class is deleted, not reconfigured, when PAC-42 closes.</b> Leaving it selectable after
 * a validated method exists would recreate the risk the M2 worksheet named.
 */
final class ProvisionalFormulaSet implements FormulaSet {

    private static final String TBD = "SOURCE_TBD";

    /**
     * Deliberately narrow. A method validated for nothing should refuse readily — PRODUCT-VIEWS #11
     * makes refusing the mitigation, not the limitation.
     */
    private static final ValidatedEnvelope ENVELOPE =
            new ValidatedEnvelope(
                    SurfaceM2.Companion.ofWholeSquareMetres(20),
                    SurfaceM2.Companion.ofWholeSquareMetres(300),
                    new CeilingHeightM(200),
                    new CeilingHeightM(350),
                    TemperatureC.Companion.ofWholeDegrees(-20),
                    TemperatureC.Companion.ofWholeDegrees(0),
                    Set.of(ConstructionPeriod.values()),
                    Set.of(InsulationLevel.values()),
                    Set.of(VentilationType.values()),
                    Set.of(EmitterType.values()));

    @Override
    public ValidatedEnvelope getEnvelope() {
        return ENVELOPE;
    }

    @Override
    public Sourced<ThermalTransmittance> uValueFor(
            ConstructionPeriod period, InsulationLevel insulation) {
        return new Sourced<>(
                new ThermalTransmittance((period.ordinal() + 1) * 1_000 + insulation.ordinal() * 100),
                TBD + " (arithmetic ramp, not a U-value; rises with newer construction, which is backwards)");
    }

    @Override
    public Sourced<AirChangeRate> airChangeRateFor(VentilationType ventilation) {
        return new Sourced<>(
                new AirChangeRate((ventilation.ordinal() + 1) * 1_000),
                TBD + " (arithmetic ramp; rises with better ventilation, which is backwards)");
    }

    @Override
    public Sourced<VolumetricHeatCapacity> airVolumetricHeatCapacity() {
        return new Sourced<>(
                new VolumetricHeatCapacity(1_000), TBD + " (unity, not the physical value of air)");
    }

    @Override
    public Sourced<EnvelopeAreaFactor> envelopeAreaFactor() {
        return new Sourced<>(
                new EnvelopeAreaFactor(1_000),
                TBD + " (unity; a real dwelling loses heat through more envelope than it has floor)");
    }

    @Override
    public Sourced<Percentage> underSizingMargin() {
        return new Sourced<>(new Percentage(1_000), TBD + " (round placeholder, not a validated tolerance)");
    }

    @Override
    public Sourced<Percentage> overSizingMargin() {
        return new Sourced<>(
                new Percentage(2_000),
                TBD + " (round placeholder, deliberately different from the under margin)");
    }

    /**
     * {@code FAN_COIL} is withheld on purpose: the withheld path has to be reachable, and a method
     * with nothing validated is exactly the one that should decline for the least common emitter.
     */
    @Override
    public FlowTemperatureGuidance flowTemperatureFor(EmitterType emitter) {
        return switch (emitter) {
            case RADIATOR_HIGH_TEMPERATURE ->
                    new FlowTemperatureGuidance.Advised(new TemperatureC(500), TBD + " (round placeholder)");
            case RADIATOR_LOW_TEMPERATURE ->
                    new FlowTemperatureGuidance.Advised(new TemperatureC(400), TBD + " (round placeholder)");
            case UNDERFLOOR_HEATING ->
                    new FlowTemperatureGuidance.Advised(new TemperatureC(300), TBD + " (round placeholder)");
            case FAN_COIL ->
                    new FlowTemperatureGuidance.Withheld(TBD + " (no guidance validated for this emitter)");
        };
    }
}
