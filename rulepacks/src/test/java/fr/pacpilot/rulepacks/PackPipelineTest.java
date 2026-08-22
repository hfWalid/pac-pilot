package fr.pacpilot.rulepacks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.pacpilot.core.aids.model.AidRulePack;
import fr.pacpilot.core.shared.EffectiveDate;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pipeline, and every refusal it exists to make.
 *
 * <p>Each refusal is driven deliberately, and each assertion checks the <b>message</b> as well as the
 * failure — because these are read by someone mid-publication with a barème page open, and
 * "validation failed" would send them to the code.
 */
class PackPipelineTest {

    @TempDir Path published;

    private PackPipeline pipeline;
    private FilePackStore store;

    @BeforeEach
    void setUp() {
        store = new FilePackStore(published);
        pipeline = new PackPipeline(store, PackSigner.fromEnvironment(PackFixtures.aSigningKey()));
    }

    @Test
    void aSourceBecomesAPublishedPackWithItsSixFields() {
        AidRulePack pack = pipeline.publish(PackFixtures.firstHalf(), "fixture");

        assertThat(pack.getVersion().getValue()).isEqualTo("test-2025-H1");
        assertThat(pack.getEffectiveFrom()).isEqualTo(new EffectiveDate(2025, 1, 1));
        assertThat(pack.getEffectiveTo()).isEqualTo(new EffectiveDate(2025, 6, 30));
        assertThat(pack.getChecksum()).hasSize(64);
        assertThat(pack.getSignature()).isNotBlank();
        assertThat(pack.getPayload().getAids()).hasSize(3);
        assertThat(pack.getPayload().getVatRate().getRate().render()).isEqualTo("10.00");
    }

    @Test
    void theSameSourceAlwaysProducesTheSameChecksum() {
        // Canonical means canonical: reflowing the file without changing a value must not move the
        // checksum, and changing any value must.
        String reflowed = PackFixtures.firstHalf().replace("id       = fixture-tiered", "id = fixture-tiered");

        assertThat(PackChecksum.of(PackSource.read(reflowed, "a")))
                .isEqualTo(PackChecksum.of(PackSource.read(PackFixtures.firstHalf(), "b")));
    }

    @Test
    void changingOneAmountChangesTheChecksum() {
        String altered = PackFixtures.firstHalf().replace("decile.1 = 1000.00", "decile.1 = 1000.01");

        assertThat(PackChecksum.of(PackSource.read(altered, "a")))
                .isNotEqualTo(PackChecksum.of(PackSource.read(PackFixtures.firstHalf(), "b")));
    }

    @Test
    void aSuccessorStartingTheDayAfterIsAccepted() {
        pipeline.publish(PackFixtures.firstHalf(), "H1");
        pipeline.publish(PackFixtures.secondHalf(), "H2");

        assertThat(store.published()).hasSize(2);
    }

    @Test
    void anOverlappingSuccessorIsRefusedByName() {
        pipeline.publish(PackFixtures.firstHalf(), "H1");
        String overlapping = PackFixtures.secondHalf().replace("effective-from = 2025-07-01", "effective-from = 2025-06-15");

        assertThatThrownBy(() -> pipeline.publish(overlapping, "sources/H2.pack"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("sources/H2.pack")
                .hasMessageContaining("overlap")
                .hasMessageContaining("test-2025-H1");
    }

    @Test
    void aGapBeforeTheSuccessorIsRefused() {
        // The quieter of the two failures: an overlap makes resolution refuse, a gap silently leaves
        // a day on which no devis can be priced at all.
        pipeline.publish(PackFixtures.firstHalf(), "H1");
        String gapped = PackFixtures.secondHalf().replace("effective-from = 2025-07-01", "effective-from = 2025-07-02");

        assertThatThrownBy(() -> pipeline.publish(gapped, "sources/H2.pack"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("gap")
                .hasMessageContaining("cannot be priced");
    }

    @Test
    void anOpenEndedPredecessorMustBeClosedFirst() {
        String openEnded = PackFixtures.firstHalf().replace("effective-to   = 2025-06-30", "");
        pipeline.publish(openEnded, "H1");

        assertThatThrownBy(() -> pipeline.publish(PackFixtures.secondHalf(), "sources/H2.pack"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("still open-ended");
    }

    @Test
    void reusingAVersionIsRefusedTwiceOver() {
        // Once by the validator against the published series, and again by the filesystem's
        // CREATE_NEW if anything ever got past it.
        pipeline.publish(PackFixtures.firstHalf(), "H1");

        assertThatThrownBy(() -> pipeline.publish(PackFixtures.firstHalf(), "sources/H1-again.pack"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("already published")
                .hasMessageContaining("publishing a successor");
    }

    @Test
    void anUnsourcedRuleIsRefused() {
        // SOURCE_TBD is legitimate in a fixture and never in a published pack. AidRule refuses a
        // blank citation; this catches the subtler case the model cannot.
        String provisional = PackFixtures.firstHalf().replace("source = FIXTURE — synthetic\namount", "source = SOURCE_TBD\namount");

        assertThatThrownBy(() -> pipeline.publish(provisional, "sources/H1.pack"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("SOURCE_TBD")
                .hasMessageContaining("fixture-forfait");
    }

    @Test
    void aMissingCitationNamesTheSectionAndTheLine() {
        String noSource = PackFixtures.firstHalf().replace("source = FIXTURE — synthetic\namount = 500.00", "amount = 500.00");

        assertThatThrownBy(() -> pipeline.publish(noSource, "sources/H1.pack"))
                .isInstanceOf(fr.pacpilot.core.aids.model.AidRulePackFormatException.class)
                .hasMessageContaining("sources/H1.pack line")
                .hasMessageContaining("aid forfait")
                .hasMessageContaining("source");
    }

    @Test
    void publishingWithoutASigningKeyStopsRatherThanPublishingUnsigned() {
        // An unsigned pack reaching a device would defeat the whole mechanism silently.
        assertThatThrownBy(() -> PackSigner.fromEnvironment(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PackSigner.KEY_VARIABLE)
                .hasMessageContaining("will not publish an unsigned pack");
    }

    @Test
    void aMalformedKeyIsRefusedWithoutEchoingIt() {
        // A malformed key is still key material.
        assertThatThrownBy(() -> PackSigner.fromEnvironment("not-a-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-a-key");
    }

    @Test
    void aPublishedPackCannotBeOverwrittenOnDisk() throws IOException {
        // The contract's last line before the storage layer's own. CREATE_NEW makes the filesystem
        // refuse atomically rather than this class checking then writing.
        AidRulePack pack = pipeline.publish(PackFixtures.firstHalf(), "H1");

        assertThatThrownBy(() -> store.publish(pack, "tampered"))
                .isInstanceOf(PackValidationException.class)
                .hasMessageContaining("immutable");
        assertThat(java.nio.file.Files.readString(published.resolve("test-2025-H1.pack")))
                .doesNotContain("tampered");
    }

    @Test
    void aPublishedPackReadsBackAsWhatWasWritten() {
        AidRulePack published1 = pipeline.publish(PackFixtures.firstHalf(), "H1");

        AidRulePack readBack = store.published().getFirst();

        assertThat(readBack.getVersion()).isEqualTo(published1.getVersion());
        assertThat(readBack.getChecksum()).isEqualTo(published1.getChecksum());
        assertThat(readBack.getSignature()).isEqualTo(published1.getSignature());
        assertThat(readBack.getPayload()).isEqualTo(published1.getPayload());
    }

    @Test
    void aPublishedPackIsStillReadableByAPerson() {
        // The artefact a device pulls is the source with a header, not a canonical hashing form —
        // so verifying what was published against what was written is reading two similar files.
        pipeline.publish(PackFixtures.firstHalf(), "H1");

        String artefact = readPublished();
        assertThat(artefact).contains("[aid income-tiered]", "decile.1 = 1000.00", "checksum = ", "signature = ");
    }

    private String readPublished() {
        try {
            return java.nio.file.Files.readString(published.resolve("test-2025-H1.pack"));
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
