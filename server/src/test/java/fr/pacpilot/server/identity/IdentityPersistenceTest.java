package fr.pacpilot.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.server.identity.application.port.out.InstallerRepository;
import fr.pacpilot.server.identity.domain.Installer;
import fr.pacpilot.server.identity.domain.Siret;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The account of record (ADR-0013) against a real PostgreSQL — the SIRET format and uniqueness
 * rules are schema decisions, and only a real database enforces them.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "pacpilot.dimensioning.method=indicative-provisional")
class IdentityPersistenceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private InstallerRepository installers;

    private static final Instant RECORDED_AT = Instant.parse("2026-08-22T09:00:00Z");
    private static final AtomicLong NEXT_SIRET = new AtomicLong(90_000_000_000_001L);

    private static Siret aSiret() {
        return new Siret(String.format("%014d", NEXT_SIRET.getAndIncrement()));
    }

    private static Installer anInstaller(Siret siret, Optional<String> qualification) {
        return new Installer(
                UUID.randomUUID(), "Chauffage Berthier", siret, qualification, RECORDED_AT, RECORDED_AT);
    }

    @Test
    void anInstallerSurvivesARoundTrip() {
        Installer saved = installers.save(anInstaller(aSiret(), Optional.of("QPAC-2026-0042")));

        assertThat(installers.findById(saved.id())).contains(saved);
        assertThat(saved.isQualificationRecorded()).isTrue();
    }

    @Test
    void anAccountExistsBeforeItsQualificationReferenceIsKnown() {
        // Blocking account creation on the RGE reference would put the system in the way of the
        // work — the artisan holds the qualification whether or not it has been typed in yet.
        Installer saved = installers.save(anInstaller(aSiret(), Optional.empty()));

        assertThat(installers.findById(saved.id()).orElseThrow().isQualificationRecorded()).isFalse();
    }

    @Test
    void twoAccountsCannotShareASiret() {
        // One business, one account. Without this, a replay that regenerated an id would create a
        // second account for the same artisan and split their work across both.
        Siret shared = aSiret();
        installers.save(anInstaller(shared, Optional.empty()));

        assertThatThrownBy(() -> installers.save(anInstaller(shared, Optional.empty())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void aMalformedSiretIsRefusedByTheTypeBeforeItReachesTheDatabase() {
        // The domain refuses first; the check constraint is the second line, for anything that
        // reaches the table by another route.
        assertThatThrownBy(() -> new Siret("1234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Siret("1234567890123X")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Siret(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRejectedSiretIsNotEchoedBackInTheMessage() {
        // A malformed SIRET is still a business identifier, and error messages outlive the request
        // that produced them. Same rule the income decile follows (M3-08).
        assertThatThrownBy(() -> new Siret("00000000000000X")).hasMessage("a SIRET is exactly 14 digits");
    }
}
