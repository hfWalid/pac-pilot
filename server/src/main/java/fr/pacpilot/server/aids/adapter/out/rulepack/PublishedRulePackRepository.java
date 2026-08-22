package fr.pacpilot.server.aids.adapter.out.rulepack;

import fr.pacpilot.core.aids.model.AidRulePack;
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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the barème in force on a devis date, from the packs the M6 pipeline published.
 *
 * <p>The server half of the promise M3-02 made when it kept caching and I/O out of
 * {@link RulePackRepository}: the same port, satisfiable from a browser with IndexedDB behind it and
 * from a server with published packs behind it. <b>The interface does not change. Only where the
 * packs come from does.</b>
 *
 * <p><b>Resolution is not reimplemented here.</b> It delegates to {@code AidRulePack.covers}, which
 * delegates to {@code EffectiveDateRange} — there is exactly one implementation of the inclusive-end
 * rule in this product, and a second one is how a devis ends up priced by one pack and recomputed
 * against another.
 *
 * <p><b>Resolution is by date, never by recency.</b> There is no "current pack" accessor and no
 * clock: a devis dated in 2026 resolves the 2026 pack in 2029, which is what M4-07's verifier reads
 * through when it recomputes a past study.
 *
 * <p><b>Caching needs no invalidation</b>, which is a gift of immutability: a published pack never
 * changes, so only the <i>set</i> of known packs grows. Packs are read once at construction; a new
 * publication is picked up on restart, which is the honest cadence for something published four
 * times a year.
 */
@org.springframework.stereotype.Component
class PublishedRulePackRepository implements RulePackRepository {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(PublishedRulePackRepository.class);

    private final List<AidRulePack> packs;

    PublishedRulePackRepository(
            @org.springframework.beans.factory.annotation.Value("${pacpilot.rulepacks.directory:}") String directory) {
        this.packs = load(directory);

        if (packs.isEmpty()) {
            // The expected state until the ⚑ gate (PAC-75) closes. ADR-0017: no barème is published,
            // so every resolution returns NoPackPublished and the aids path refuses honestly.
            LOG.warn(
                    "No rule packs published — every aids resolution will refuse (ADR-0017). "
                            + "Set pacpilot.rulepacks.directory once PAC-75 has published the first barèmes.");
        } else {
            LOG.info("Loaded {} published rule pack(s), checksums verified", packs.size());
        }
    }

    /**
     * The pack in force on {@code effectiveDate}, or {@code null} when none is.
     *
     * <p>Two packs matching one date fails loudly rather than picking the newest — a publication
     * error the pipeline should have refused, and choosing silently would hide it until an auditor
     * found it. That matters more here than in memory, because the store is where a bad publication
     * actually lands.
     */
    @Override
    public AidRulePack packEffectiveOn(EffectiveDate effectiveDate) {
        List<AidRulePack> matching = packs.stream().filter(pack -> pack.covers(effectiveDate)).toList();

        if (matching.size() > 1) {
            throw new IllegalStateException(
                    "packs overlap on "
                            + effectiveDate.render()
                            + ": "
                            + matching.stream().map(pack -> pack.getVersion().getValue()).toList()
                            + " — a publication error; every devis priced in the overlap is suspect");
        }
        return matching.isEmpty() ? null : matching.getFirst();
    }

    private static List<AidRulePack> load(String directory) {
        if (directory == null || directory.isBlank() || !Files.isDirectory(Path.of(directory))) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(Path.of(directory))) {
            List<AidRulePack> loaded = new ArrayList<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".pack")).sorted().toList()) {
                loaded.add(
                        verified(
                                AidRulePackFormat.INSTANCE.readPublished(
                                        Files.readString(file, StandardCharsets.UTF_8), file.toString()),
                                file));
            }
            return List.copyOf(loaded);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * A pack whose checksum does not match is refused, never used degraded.
     *
     * <p>The checksum exists so that somebody finds out. Loading it anyway — even with a warning —
     * would mean a tampered barème priced a devis, which is the one outcome the mechanism is for.
     */
    private static AidRulePack verified(AidRulePack pack, Path file) {
        String recomputed = sha256(AidRulePackCanonicalForm.INSTANCE.of(pack));
        if (!recomputed.equals(pack.getChecksum())) {
            throw new IllegalStateException(
                    file
                            + ": checksum mismatch — recorded "
                            + pack.getChecksum()
                            + ", recomputed "
                            + recomputed
                            + ". The pack was altered after publication; it will not be used.");
        }
        return pack;
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
