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
 * Verifies the migration chain against a real PostgreSQL of the same major version as
 * docker-compose and production. An in-memory substitute would prove nothing: migrations are
 * dialect-specific, and the whole point of Flyway here is that what runs locally is what runs in
 * the FR region.
 *
 * <p><b>Was {@code FlywayBaselineMigrationTest} until M4-03.</b> It asserted that exactly one
 * migration existed and counted rows in {@code schema_baseline} — both true while V1 was the only
 * migration, and both false the moment V2 created real schema and dropped the placeholder the
 * baseline had left behind. Renamed rather than patched, because it is no longer a test about a
 * baseline.
 *
 * <p>{@code disabledWithoutDocker} keeps {@code ./gradlew build} runnable on a machine with no
 * Docker daemon — the test skips rather than failing. A green summary on such a machine does not
 * mean this ran; check the XML for {@code skipped="0"}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class SchemaMigrationsTest {

    // Must track the image pinned in docker-compose.yml: testing against a different major than the
    // one developers and production run would defeat the purpose.
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private DataSource dataSource;

    private record AppliedMigration(String version, String description, boolean success) {}

    @Test
    void appliesTheWholeChainInOrderAndSuccessfully() throws SQLException {
        assertThat(appliedMigrations())
                .describedAs("every migration on the classpath, in version order, all successful")
                .containsExactly(
                        new AppliedMigration("1", "baseline", true),
                        new AppliedMigration("2", "dossier client and site", true),
                        new AppliedMigration("3", "identity installer", true),
                        new AppliedMigration("4", "dimensioning study", true),
                        new AppliedMigration("5", "quoting quote", true),
                        new AppliedMigration("6", "catalog reference data", true),
                        new AppliedMigration("7", "dimensioning effective date and verification", true));
    }

    @Test
    void migrationIsIdempotentAcrossRestarts() throws SQLException {
        // Flyway already ran once when the context started. Running it again must be a no-op: if a
        // restart re-applied migrations, every deployment would corrupt its own database.
        List<AppliedMigration> before = appliedMigrations();

        var result =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();

        assertThat(result.migrationsExecuted)
                .describedAs("a second migrate() must apply nothing")
                .isZero();
        assertThat(appliedMigrations())
                .describedAs("the history is unchanged by a second run")
                .isEqualTo(before);
    }

    @Test
    void theBaselinePlaceholderIsGoneOnceRealSchemaExists() throws SQLException {
        // V1 created schema_baseline and said in a comment that M4 should remove it. V2 does.
        // Asserted rather than assumed, because a placeholder table that outlives its purpose is
        // exactly the kind of thing that is still there three years later.
        assertThat(tableExists("schema_baseline")).isFalse();
        assertThat(tableExists("dossier_client")).isTrue();
        assertThat(tableExists("dossier_site")).isTrue();
        assertThat(tableExists("identity_installer")).isTrue();
        assertThat(tableExists("dimensioning_study")).isTrue();
        assertThat(tableExists("dimensioning_assumption")).isTrue();
        assertThat(tableExists("quoting_quote")).isTrue();
        assertThat(tableExists("quoting_line_item")).isTrue();
        assertThat(tableExists("quoting_aid_line")).isTrue();
        assertThat(tableExists("catalog_departement_climate")).isTrue();
        assertThat(tableExists("catalog_product")).isTrue();
        assertThat(tableExists("dimensioning_verification")).isTrue();
    }

    private List<AppliedMigration> appliedMigrations() throws SQLException {
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
        return applied;
    }

    private boolean tableExists(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "select count(*) from information_schema.tables"
                                        + " where table_schema = 'public' and table_name = '"
                                        + name
                                        + "'")) {
            rows.next();
            return rows.getInt(1) > 0;
        }
    }
}
