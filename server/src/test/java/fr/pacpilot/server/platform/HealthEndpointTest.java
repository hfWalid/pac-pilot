package fr.pacpilot.server.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the context loads and the server is linked against the core's JVM target.
 *
 * <p>Runs <b>without a database</b>. M0-05 wired a DataSource and Flyway, so the autoconfiguration
 * is excluded here deliberately: the health endpoint has no persistence concern, and making the
 * fast feedback loop depend on a running container would be a poor trade. The migration itself is
 * verified against a real PostgreSQL in {@link FlywayBaselineMigrationTest}.
 */
@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
@AutoConfigureMockMvc
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
