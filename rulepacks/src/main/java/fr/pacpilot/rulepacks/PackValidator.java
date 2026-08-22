package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRule;
import fr.pacpilot.core.aids.model.AidRulePack;
import fr.pacpilot.core.shared.EffectiveDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where most defects should die.
 *
 * <p>A pack that reaches the store with an overlapping range or an unsourced rule has already cost
 * more to fix than to prevent: every devis priced against it has to be re-examined, and packs are
 * immutable so the fix is a corrective successor rather than an edit.
 *
 * <p>Several invariants are <b>not</b> re-checked here because the model already refuses them —
 * {@code AidRule} rejects a blank citation, {@code AidRulePackPayload} rejects duplicate ids, and
 * {@code EffectiveDateRange} rejects a backward range. Re-implementing those would be a second
 * opinion that could disagree with the first. What this class adds is the checks that need sight of
 * something a single pack cannot see: <b>the published series</b>.
 */
final class PackValidator {

    private PackValidator() {}

    /**
     * @param alreadyPublished the whole published series, in any order.
     * @throws PackValidationException naming the file and the cause.
     */
    static void validate(PackSource source, List<AidRulePack> alreadyPublished) {
        refuseDuplicateVersion(source, alreadyPublished);
        refuseUnsourcedRules(source);
        refuseOverlapOrGap(source, alreadyPublished);
    }

    private static void refuseDuplicateVersion(PackSource source, List<AidRulePack> published) {
        boolean exists = published.stream().anyMatch(pack -> pack.getVersion().equals(source.version()));
        if (exists) {
            throw new PackValidationException(
                    source.origin()
                            + ": version '"
                            + source.version().getValue()
                            + "' is already published. Packs are immutable — correct a mistake by"
                            + " publishing a successor with a new version, never by reusing one.");
        }
    }

    /**
     * A citation that is not re-checkable a year later is not a citation.
     *
     * <p>{@code AidRule} already refuses a blank source, so this catches the subtler case the model
     * cannot: {@code SOURCE_TBD}, which is legitimate in a test fixture and never in a published pack.
     */
    private static void refuseUnsourcedRules(PackSource source) {
        for (AidRule rule : source.aids()) {
            if (rule.isProvisional()) {
                throw new PackValidationException(
                        source.origin()
                                + ": rule '"
                                + rule.getId().getValue()
                                + "' still carries "
                                + AidRule.SOURCE_TBD
                                + ". A published pack states where every figure came from; the ⚑ gate"
                                + " (PAC-75) is where those citations are checked against the official"
                                + " source.");
            }
        }
        if (source.vatRate().isProvisional()) {
            throw new PackValidationException(
                    source.origin() + ": the VAT rate still carries " + AidRule.SOURCE_TBD);
        }
    }

    /**
     * The check that cannot live anywhere else.
     *
     * <p>M3-02 decided resolution fails loudly when two packs match a date — that is the safety net.
     * This is the prevention, and it is the only place with sight of the whole published series.
     *
     * <p>Both failures matter and they fail differently: an <b>overlap</b> makes resolution refuse
     * outright, while a <b>gap</b> silently leaves a day on which no devis can be priced at all.
     */
    private static void refuseOverlapOrGap(PackSource source, List<AidRulePack> published) {
        if (published.isEmpty()) {
            return;
        }

        Optional<AidRulePack> latest =
                published.stream().max(Comparator.comparing(AidRulePack::getEffectiveFrom));
        AidRulePack predecessor = latest.orElseThrow();

        if (source.effectiveFrom().compareTo(predecessor.getEffectiveFrom()) <= 0) {
            throw new PackValidationException(
                    source.origin()
                            + ": starts on "
                            + source.effectiveFrom().render()
                            + ", which is not after the latest published pack '"
                            + predecessor.getVersion().getValue()
                            + "' starting "
                            + predecessor.getEffectiveFrom().render()
                            + ". Packs are published in order.");
        }

        EffectiveDate predecessorEnd = predecessor.getEffectiveTo();
        if (predecessorEnd == null) {
            throw new PackValidationException(
                    source.origin()
                            + ": the latest published pack '"
                            + predecessor.getVersion().getValue()
                            + "' is still open-ended. Close it with an effective-to before publishing a"
                            + " successor, or the two overlap forever.");
        }

        if (source.effectiveFrom().compareTo(predecessorEnd) <= 0) {
            throw new PackValidationException(
                    source.origin()
                            + ": starts "
                            + source.effectiveFrom().render()
                            + " but '"
                            + predecessor.getVersion().getValue()
                            + "' runs to "
                            + predecessorEnd.render()
                            + " inclusive — they overlap. A devis dated in the overlap resolves to"
                            + " neither, because resolution refuses rather than choosing.");
        }

        if (!isDayAfter(predecessorEnd, source.effectiveFrom())) {
            throw new PackValidationException(
                    source.origin()
                            + ": starts "
                            + source.effectiveFrom().render()
                            + " but '"
                            + predecessor.getVersion().getValue()
                            + "' ended "
                            + predecessorEnd.render()
                            + " — that leaves a gap. A devis dated inside it cannot be priced at all,"
                            + " and nothing else would report that.");
        }
    }

    /** {@code effectiveTo} is inclusive (M1-07), so a successor starts the very next day. */
    private static boolean isDayAfter(EffectiveDate end, EffectiveDate start) {
        int lastDay = EffectiveDate.Companion.lengthOfMonth(end.getYear(), end.getMonth());
        if (end.getDay() < lastDay) {
            return start.getYear() == end.getYear()
                    && start.getMonth() == end.getMonth()
                    && start.getDay() == end.getDay() + 1;
        }
        if (end.getMonth() < 12) {
            return start.getYear() == end.getYear() && start.getMonth() == end.getMonth() + 1 && start.getDay() == 1;
        }
        return start.getYear() == end.getYear() + 1 && start.getMonth() == 1 && start.getDay() == 1;
    }
}
