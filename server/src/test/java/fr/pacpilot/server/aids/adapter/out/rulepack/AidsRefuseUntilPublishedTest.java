package fr.pacpilot.server.aids.adapter.out.rulepack;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.aids.model.AidsInputs;
import fr.pacpilot.core.aids.model.AidsOutcome;
import fr.pacpilot.core.aids.model.HeatPumpType;
import fr.pacpilot.core.aids.model.IncomeDecile;
import fr.pacpilot.core.aids.model.ReplacedSystem;
import fr.pacpilot.core.aids.port.ResolveAids;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * ADR-0017 as behaviour: the aids engine is wired and working, and refuses because no barème is
 * published — not because a bean is missing.
 *
 * <p>A slice rather than {@code @SpringBootTest}, for the same reason as
 * {@code DimensioningMethodConfigurationTest}: this is about wiring, and a full context would drag
 * in a database that has nothing to do with the claim.
 */
class AidsRefuseUntilPublishedTest {

    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(AidsEngineConfiguration.class, PublishedRulePackRepository.class)
                    .withPropertyValues("pacpilot.rulepacks.directory=");

    private static final AidsInputs INPUTS =
            new AidsInputs(
                    new IncomeDecile(3),
                    HeatPumpType.AIR_WATER,
                    ClimateZone.H1,
                    ReplacedSystem.OIL_BOILER,
                    MoneyEur.Companion.ofEuros(14_000));

    @Test
    void theEngineIsWiredAndRefusesRatherThanBeingAbsent() {
        // The wiring is exercised now so that M6 changes the data behind the port and nothing else.
        context.run(
                started -> {
                    assertThat(started).hasSingleBean(ResolveAids.class);
                    assertThat(
                                    started
                                            .getBean(ResolveAids.class)
                                            .resolve(INPUTS, new EffectiveDate(2026, 8, 22)))
                            .isInstanceOf(AidsOutcome.NoPackPublished.class);
                });
    }

    @Test
    void everyDateRefuses_becauseNoBaremeIsPublishedAtAll() {
        // Not a gap in coverage — a deliberate absence. A placeholder barème produces "vous toucherez
        // 4 000 €", which a homeowner remembers and budgets around (ADR-0017).
        context.run(
                started -> {
                    ResolveAids aids = started.getBean(ResolveAids.class);
                    for (EffectiveDate date :
                            new EffectiveDate[] {
                                new EffectiveDate(2020, 1, 1),
                                new EffectiveDate(2025, 6, 30),
                                new EffectiveDate(2026, 8, 22),
                                new EffectiveDate(2030, 12, 31),
                            }) {
                        assertThat(aids.resolve(INPUTS, date))
                                .as("on %s", date.render())
                                .isInstanceOf(AidsOutcome.NoPackPublished.class);
                    }
                });
    }

    @Test
    void aRefusalIsDistinguishableFromZeroAids() {
        // Zero is a claim about the household; a refusal is a statement about the system. The API
        // must never collapse the two, and the outcome type is what makes that possible.
        context.run(
                started -> {
                    var outcome =
                            started.getBean(ResolveAids.class).resolve(INPUTS, new EffectiveDate(2026, 8, 22));

                    assertThat(outcome).isNotInstanceOf(AidsOutcome.Resolved.class);
                    assertThat(((AidsOutcome.NoPackPublished) outcome).getEffectiveDate())
                            .isEqualTo(new EffectiveDate(2026, 8, 22));
                });
    }
}
