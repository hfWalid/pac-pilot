package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRule;
import fr.pacpilot.core.aids.model.AidRulePack;
import fr.pacpilot.core.aids.model.AidRulePackPayload;
import fr.pacpilot.core.aids.model.AidRulePackVersion;
import fr.pacpilot.core.aids.model.VatRate;
import fr.pacpilot.core.shared.EffectiveDate;
import java.util.List;
import java.util.Optional;

/**
 * An encoded barème, parsed but not yet validated, checksummed or signed.
 *
 * <p>Deliberately a separate type from {@link AidRulePack}: a source is something a human wrote and
 * may have got wrong, while a pack is something the pipeline has checked. Collapsing them would mean
 * an unvalidated source and a published pack were the same type, and the only thing keeping them
 * apart would be where the object happened to come from.
 */
public record PackSource(
        AidRulePackVersion version,
        EffectiveDate effectiveFrom,
        Optional<EffectiveDate> effectiveTo,
        VatRate vatRate,
        List<AidRule> aids,
        /** Where this came from, for error messages a person can act on. */
        String origin) {

    public PackSource {
        aids = List.copyOf(aids);
    }

    /**
     * Assembles the pack. Called only by the pipeline, and only after validation.
     *
     * <p>The checksum and signature arrive from outside rather than being computed here: a source
     * that could sign itself would make the signing step optional by accident.
     */
    AidRulePack intoPack(String checksum, String signature) {
        return new AidRulePack(
                version,
                effectiveFrom,
                effectiveTo.orElse(null),
                new AidRulePackPayload(vatRate, aids),
                checksum,
                signature);
    }
}
