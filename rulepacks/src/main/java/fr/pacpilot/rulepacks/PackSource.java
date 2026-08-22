package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRule;
import fr.pacpilot.core.aids.model.AidRulePack;
import fr.pacpilot.core.aids.model.AidRulePackFormat;
import fr.pacpilot.core.aids.model.AidRulePackPayload;
import fr.pacpilot.core.aids.model.AidRulePackVersion;
import fr.pacpilot.core.aids.model.VatRate;
import fr.pacpilot.core.shared.EffectiveDate;
import java.util.List;
import java.util.Optional;

/**
 * A parsed source, plus where it came from.
 *
 * <p>The parsing itself is {@code :core}'s ({@link AidRulePackFormat}), because the device and the
 * server read the same format — three parsers would eventually disagree about what a file means.
 * What this adds is the {@code origin}, so every refusal names the file a person has to go and fix.
 *
 * <p>Kept distinct from {@link AidRulePack}: a source is something a human wrote and may have got
 * wrong, a pack is something the pipeline has checked. Collapsing them would leave only provenance
 * to tell them apart.
 */
public record PackSource(AidRulePackFormat.Source parsed, String origin) {

    static PackSource read(String content, String origin) {
        return new PackSource(AidRulePackFormat.INSTANCE.readSource(content, origin), origin);
    }

    public AidRulePackVersion version() {
        return parsed.getVersion();
    }

    public EffectiveDate effectiveFrom() {
        return parsed.getEffectiveFrom();
    }

    public Optional<EffectiveDate> effectiveTo() {
        return Optional.ofNullable(parsed.getEffectiveTo());
    }

    public VatRate vatRate() {
        return parsed.getVatRate();
    }

    public List<AidRule> aids() {
        return parsed.getAids();
    }

    AidRulePackPayload payload() {
        return new AidRulePackPayload(vatRate(), aids());
    }

    AidRulePack intoPack(String checksum, String signature) {
        return new AidRulePack(
                version(), effectiveFrom(), parsed.getEffectiveTo(), payload(), checksum, signature);
    }
}
