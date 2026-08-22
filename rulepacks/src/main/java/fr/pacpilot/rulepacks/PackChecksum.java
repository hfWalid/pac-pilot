package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * SHA-256 over a canonical rendering of the pack's content.
 *
 * <p><b>Canonical means the same source always yields the same checksum</b> — the same discipline as
 * the PDF determinism one layer up (PAC-66), and for the same reason: a checksum that moved between
 * runs would verify nothing.
 *
 * <p>So the rendering below fixes everything that could vary: rules sorted by id, deciles sorted
 * numerically, amounts as exact minor units rather than formatted decimals, and no timestamp, no
 * locale and no map iteration order anywhere. It deliberately does <b>not</b> reuse the
 * {@code render()} methods — those are presentation and may one day change, whereas this must not.
 *
 * <p>The version and date range are covered too. A pack whose payload is identical to its
 * predecessor's but whose range differs is a different pack, and must not share a checksum.
 */
final class PackChecksum {

    private PackChecksum() {}

    static String of(PackSource source) {
        return HexFormat.of().formatHex(sha256(canonical(source)));
    }

    static String canonical(PackSource source) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("version=").append(source.version().getValue()).append('\n');
        canonical.append("from=").append(source.effectiveFrom().render()).append('\n');
        canonical
                .append("to=")
                .append(source.effectiveTo().map(fr.pacpilot.core.shared.EffectiveDate::render).orElse(""))
                .append('\n');
        canonical
                .append("vat=")
                .append(source.vatRate().getRate().getBasisPoints())
                .append('|')
                .append(source.vatRate().getSource())
                .append('\n');

        source.aids().stream()
                .sorted(Comparator.comparing(rule -> rule.getId().getValue()))
                .forEach(rule -> canonical.append(render(rule)).append('\n'));

        return canonical.toString();
    }

    private static String render(AidRule rule) {
        String head = rule.getId().getValue() + "|" + rule.getLabel() + "|" + rule.getSource() + "|";
        return switch (rule) {
            case AidRule.IncomeTiered tiered ->
                    head
                            + "income-tiered|"
                            + tiered.getAmountByDecile().entrySet().stream()
                                    .sorted(Comparator.comparingInt(entry -> entry.getKey().getValue()))
                                    .map(entry -> entry.getKey().getValue() + ":" + entry.getValue().getCents())
                                    .collect(Collectors.joining(","))
                            + "|cap="
                            + (tiered.getCap() == null ? "" : tiered.getCap().getCents());
            case AidRule.Forfait forfait -> head + "forfait|" + forfait.getAmount().getCents();
            case AidRule.RateBased rate ->
                    head
                            + "rate-based|"
                            + rate.getRate().getBasisPoints()
                            + "|cap="
                            + (rate.getCap() == null ? "" : rate.getCap().getCents());
        };
    }

    private static byte[] sha256(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every JVM", impossible);
        }
    }
}
