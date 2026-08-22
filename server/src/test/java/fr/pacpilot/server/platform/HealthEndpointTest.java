package fr.pacpilot.server.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.pacpilot.server.platform.adapter.in.web.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the context loads and the server is linked against the core's JVM target.
 *
 * <p>Runs <b>without a database</b>, which is the whole point: the health endpoint has no
 * persistence concern and its feedback loop should not wait on a container. The migration itself is
 * verified against a real PostgreSQL in {@link FlywayBaselineMigrationTest}.
 *
 * <p><b>A web slice since M4-03, not a full context with the datasource excluded.</b> The previous
 * arrangement started every bean in the application and then subtracted the ones it did not want,
 * so the first persistence adapter to arrive — a {@code @Repository} needing a Spring Data
 * interface that autoconfiguration no longer created — broke a test about an endpoint that touches
 * neither. Naming the one controller under test says what this verifies and stops it failing for
 * reasons that belong to other contexts.
 */
@WebMvcTest(HealthController.class)
class HealthEndpointTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthReportsUpAndTheLinkedCore() throws Exception {
        var body =
                mockMvc.perform(get("/health"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(body).contains("\"status\":\"UP\"");
        assertThat(body).contains("\"core\":\"pac-pilot-core\"");
    }
}
