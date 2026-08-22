package fr.pacpilot.server.aids.adapter.out.rulepack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.core.aids.model.AidRulePackCanonicalForm;
import fr.pacpilot.core.aids.model.AidRulePackFormat;
import fr.pacpilot.core.aids.port.RulePackRepository;
import fr.pacpilot.core.shared.EffectiveDate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The server half of the promise M3-02 made: the same port, satisfied from published packs.
 *
 * <p>Every fixture here is synthetic and says so — real barèmes arrive only at the ⚑ gate (PAC-75),
 * verified by a human.
 */
class PublishedRulePackRepositoryTest {

    @TempDir Path store;

    private static String source(String version, String from, String to, long forfaitCents) {
        return "version        = " + version + "\n"
                + "effective-from = " + from + "\n"
                + (to == null ? "" : "effective-to   = " + to + "\n")
                + "\n[vat]\nrate   = 10.00\nsource = FIXTURE — synthetic\n"
                + "\n[aid forfait]\n"
                + "id     = fixture-forfait\n"
                + "label  = Forfait (fixture)\n"
                + "source = FIXTURE — synthetic\n"
                + "amount = " + (forfaitCents / 100) + "." + String.format("%02d", forfaitCents % 100) + "\n";
    }

    /** Writes a published artefact with a correct checksum, as the M6 pipeline would. */
    private void publish(String version, String from, String to, long forfaitCents) {
        String body = source(version, from, to, forfaitCents);
        var parsed = AidRulePackFormat.INSTANCE.readSource(body, version);
        String checksum =
                sha256(
                        AidRulePackCanonicalForm.INSTANCE.of(
                                parsed.getVersion(),
                                parsed.getEffectiveFrom(),
                                parsed.getEffectiveTo(),
                                new fr.pacpilot.core.aids.model.AidRulePackPayload(parsed.getVatRate(), parsed.getAids())));
        write(version, AidRulePackFormat.INSTANCE.writePublished(body, checksum, "fixture-signature"));
    }

    private void write(String version, String content) {
        try {
            Files.writeString(store.resolve(version + ".pack"), content, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private RulePackRepository repository() {
        return new PublishedRulePackRepository(store.toString());
    }

    @Test
    void anUnsetDirectoryPublishesNothingAndEveryResolutionRefuses() {
        // The state until PAC-75 closes (ADR-0017). Not an error — the honest answer.
        assertThat(new PublishedRulePackRepository("").packEffectiveOn(new EffectiveDate(2026, 8, 22))).isNull();
    }

    @Test
    void aPastDevisResolvesItsOwnPackWhileANewerOneIsPublished() {
        // The reproduction guarantee, against the real store this time. The failure mode is
        // "latest wins", and only the date decides.
        publish("test-2025-H1", "2025-01-01", "2025-06-30", 50_000);
        publish("test-2025-H2", "2025-07-01", null, 80_000);

        var repository = repository();

        assertThat(repository.packEffectiveOn(new EffectiveDate(2025, 3, 1)).getVersion().getValue())
                .isEqualTo("test-2025-H1");
        assertThat(repository.packEffectiveOn(new EffectiveDate(2026, 3, 1)).getVersion().getValue())
                .isEqualTo("test-2025-H2");
    }

    @Test
    void bothBoundaryDaysBelongToTheirOwnPack() {
        // effectiveTo is inclusive (M1-07), and this adapter must not introduce a second reading.
        publish("test-2025-H1", "2025-01-01", "2025-06-30", 50_000);
        publish("test-2025-H2", "2025-07-01", null, 80_000);

        var repository = repository();

        assertThat(repository.packEffectiveOn(new EffectiveDate(2025, 6, 30)).getVersion().getValue())
                .isEqualTo("test-2025-H1");
        assertThat(repository.packEffectiveOn(new EffectiveDate(2025, 7, 1)).getVersion().getValue())
                .isEqualTo("test-2025-H2");
    }

    @Test
    void aDateBeforeTheFirstPackResolvesToNothing() {
        publish("test-2025-H1", "2025-01-01", "2025-06-30", 50_000);

        assertThat(repository().packEffectiveOn(new EffectiveDate(2024, 12, 31))).isNull();
    }

    @Test
    void aTamperedPackIsRefusedRatherThanUsedDegraded() {
        // The checksum exists so that somebody finds out. Loading it anyway — even with a warning —
        // would mean a tampered barème priced a devis.
        publish("test-2025-H1", "2025-01-01", "2025-06-30", 50_000);
        String tampered =
                readPublished("test-2025-H1").replace("amount = 500.00", "amount = 5000.00");
        write("test-2025-H1", tampered);

        assertThatThrownBy(this::repository)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch")
                .hasMessageContaining("altered after publication");
    }

    @Test
    void overlappingPacksFailLoudlyRatherThanResolvingArbitrarily() {
        // The pipeline should have refused this. If one reaches the store anyway, choosing silently
        // would hide a bad publication until an auditor found it.
        publish("test-2025-H1", "2025-01-01", "2025-06-30", 50_000);
        publish("test-2025-overlap", "2025-06-01", "2025-12-31", 80_000);

        var repository = repository();

        assertThatThrownBy(() -> repository.packEffectiveOn(new EffectiveDate(2025, 6, 15)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("overlap")
                .hasMessageContaining("every devis priced in the overlap is suspect");
    }

    private String readPublished(String version) {
        try {
            return Files.readString(store.resolve(version + ".pack"), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
