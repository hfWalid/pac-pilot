package fr.pacpilot.server.aids.adapter.out.rulepack;

import fr.pacpilot.core.aids.model.AidRulePack;
import fr.pacpilot.core.aids.port.RulePackRepository;
import fr.pacpilot.core.shared.EffectiveDate;
import org.springframework.stereotype.Component;

/**
 * Resolves no pack, for any date, on purpose (ADR-0017).
 *
 * <p>Not a stub awaiting completion — a decision. The server ships no barème until M6's pipeline
 * publishes real, human-verified ones, so every resolution returns {@code NoPackPublished} and the
 * aids path refuses explicitly.
 *
 * <p><b>Why this differs from the dimensioning side</b>, which does ship a provisional formula set
 * (ADR-0015): a placeholder coefficient produces a meaningless heat load that nobody reads as a
 * promise, while a placeholder barème produces "vous toucherez 4 000 €" — a sentence a homeowner
 * remembers and budgets around. And unlike the formula set, nothing is blocked by refusing:
 * {@code AidsOutcome.NoPackPublished} has been a first-class outcome since M3-03, built for exactly
 * this case.
 *
 * <p>When M6 lands, this class is replaced by a repository reading published packs. The engine, the
 * port and the outcome type do not change.
 */
@Component
class UnpublishedRulePackRepository implements RulePackRepository {

    @Override
    public AidRulePack packEffectiveOn(EffectiveDate effectiveDate) {
        return null;
    }
}
