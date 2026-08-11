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
 * Deliberately runs without a database: nothing may be wired to Postgres before M0-05.
 * If this test starts needing a running database, something has been coupled too early.
 */
@SpringBootTest
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
