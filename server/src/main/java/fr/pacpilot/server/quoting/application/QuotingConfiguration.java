package fr.pacpilot.server.quoting.application;

import fr.pacpilot.core.quoting.engine.QuoteAssembler;
import fr.pacpilot.core.quoting.port.BuildQuote;
import fr.pacpilot.core.shared.QuoteId;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the core's quote assembler.
 *
 * <p>The id generator is supplied here rather than reached for inside the core, which has no source
 * of randomness by design. On the device a devis is created offline with a client-generated id
 * ({@code CLAUDE.md} §4.3); server-side there is no device, so the server mints one — and that is a
 * server concern, visible in this one line.
 */
@Configuration
class QuotingConfiguration {

    @Bean
    BuildQuote buildQuote() {
        return new QuoteAssembler(() -> new QuoteId(UUID.randomUUID().toString()));
    }
}
