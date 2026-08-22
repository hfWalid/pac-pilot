package fr.pacpilot.server.aids.adapter.out.rulepack;

import fr.pacpilot.core.aids.engine.AidsEngine;
import fr.pacpilot.core.aids.port.ResolveAids;
import fr.pacpilot.core.aids.port.RulePackRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the M3 aids engine over whatever pack source this deployment has.
 *
 * <p>Today that source publishes nothing (ADR-0017), so the engine is wired and functioning and
 * every resolution refuses. That is deliberate: the wiring is exercised now, so M6 changes the data
 * behind the port and nothing else.
 */
@Configuration
class AidsEngineConfiguration {

    @Bean
    ResolveAids resolveAids(RulePackRepository rulePacks) {
        return new AidsEngine(rulePacks);
    }
}
