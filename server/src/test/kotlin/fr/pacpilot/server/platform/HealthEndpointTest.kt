package fr.pacpilot.server.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Proves the context loads and the server is linked against the core's JVM target.
 *
 * Runs **without a database**. M0-05 wired a DataSource and Flyway, so the autoconfiguration is
 * excluded here deliberately: the health endpoint has no persistence concern, and making the fast
 * feedback loop depend on a running container would be a poor trade. The migration itself is
 * verified against a real PostgreSQL in [fr.pacpilot.server.platform.FlywayBaselineMigrationTest].
 */
@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    ],
)
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health reports UP and the linked core`() {
        val body = mockMvc.get("/health")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        assertThat(body).contains("\"status\":\"UP\"")
        assertThat(body).contains("\"core\":\"pac-pilot-core\"")
    }
}
