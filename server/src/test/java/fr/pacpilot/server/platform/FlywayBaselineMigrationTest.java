package fr.pacpilot.server.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the baseline migration against a real PostgreSQL of the same major version as
 * docker-compose and production. An in-memory substitute would prove nothing: migrations are
 * dialect-specific, and the whole point of Flyway here is that what runs locally is what runs in
 * the FR region.
 *
 * <p>{@code disabledWithoutDocker} keeps {@code ./gradlew build} runnable on a machine with no
 * Docker daemon — the test skips rather than failing. CI has Docker, so it does run there (M0-08).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class FlywayBaselineMigrationTest {

    // Must track the image pinned in docker-compose.yml: testing against a different major than the
    // one developers and production run would defeat the purpose.
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private DataSource dataSource;

    private record AppliedMigration(String version, String description, boolean success) {}

    @Test
    void appliesExactlyTheBaselineMigrationSuccessfully() throws SQLException {
        List<AppliedMigration> applied = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "select version, description, success from flyway_schema_history"
                                        + " order by installed_rank")) {
            while (rows.next()) {
                applied.add(
                        new AppliedMigration(
                                rows.getString("version"),
                                rows.getString("description"),
                                rows.getBoolean("success")));
            }
        }

        assertThat(applied)
                .describedAs("exactly one migration, applied successfully")
                .containsExactly(new AppliedMigration("1", "baseline", true));
    }

    @Test
    void migrationIsIdempotentAcrossRestarts() throws SQLException {
        // Flyway already ran once when the context started. Running it again must be a no-op: if a
        // restart re-applied migrations, every deployment would corrupt its own database.
        int countBefore = countBaselineRows();

        var result =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();

        assertThat(result.migrationsExecuted)
                .describedAs("a second migrate() must apply nothing")
                .isZero();
        assertThat(countBaselineRows())
                .describedAs("no duplicate baseline row")
                .isEqualTo(countBefore);
    }

    private int countBaselineRows() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select count(*) from schema_baseline")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
