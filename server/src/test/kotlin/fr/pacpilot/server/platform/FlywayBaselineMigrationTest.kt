package fr.pacpilot.server.platform

import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Verifies the baseline migration against a real PostgreSQL of the same major version as
 * docker-compose and production. An in-memory substitute would prove nothing: migrations are
 * dialect-specific, and the whole point of Flyway here is that what runs locally is what runs in
 * the FR region.
 *
 * `disabledWithoutDocker` keeps `./gradlew build` runnable on a machine with no Docker daemon —
 * the test skips rather than failing. CI must have Docker, so it does run there (M0-08).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class FlywayBaselineMigrationTest {

    companion object {
        // Must track the image pinned in docker-compose.yml: testing against a different major
        // than the one developers and production run would defeat the purpose.
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `applies exactly the baseline migration, successfully`() {
        val applied = dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "select version, description, success from flyway_schema_history order by installed_rank",
                ).use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                Triple(
                                    rows.getString("version"),
                                    rows.getString("description"),
                                    rows.getBoolean("success"),
                                ),
                            )
                        }
                    }
                }
            }
        }

        assertThat(applied)
            .describedAs("exactly one migration, applied successfully")
            .containsExactly(Triple("1", "baseline", true))
    }

    @Test
    fun `migration is idempotent across restarts`() {
        // Flyway already ran once when the context started. Running it again must be a no-op:
        // if a restart re-applied migrations, every deployment would corrupt its own database.
        val countBefore = countBaselineRows()

        val flyway = org.flywaydb.core.Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        val result = flyway.migrate()

        assertThat(result.migrationsExecuted)
            .describedAs("a second migrate() must apply nothing")
            .isZero()
        assertThat(countBaselineRows())
            .describedAs("no duplicate baseline row")
            .isEqualTo(countBefore)
    }

    private fun countBaselineRows(): Int =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from schema_baseline").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}
