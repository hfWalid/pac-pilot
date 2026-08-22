package fr.pacpilot.server.platform.adapter.out.document;

import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * French presentation of the core's canonical values (PAC-65).
 *
 * <p><b>The core stays locale-free on purpose.</b> {@code MoneyEur.render()} gives {@code 1234.56}
 * and {@code EffectiveDate.render()} gives {@code 2026-08-21} because those are what the golden
 * vectors compare across the JVM and JS targets. Localisation is presentation, and pushing it inward
 * would break that cross-target contract.
 *
 * <p><b>No {@code Locale}, no {@code NumberFormat}, no {@code DateTimeFormatter} for numbers.</b>
 * Every method here is a pure string transformation of a canonical value. That is a stronger
 * guarantee than pinning {@code fr-FR}: there is no locale data to read, so there is nothing for a
 * hostile system locale to change and nothing for a JDK upgrade's CLDR revision to move. A pinned
 * locale would still leave the output at the mercy of the platform's idea of what French looks like.
 *
 * <p><b>Non-breaking spaces are used where French typography requires them</b> — before {@code €},
 * {@code %} and inside thousands groups. A line-wrapped {@code 1 234\n,56 €} on a printed devis reads
 * as a defect. U+00A0 specifically, not the narrow U+202F: WinAnsiEncoding covers the former and the
 * standard-14 fonts of ADR-0018 cannot render the latter.
 */
final class FrenchFormat {

    /** U+00A0. Present in WinAnsiEncoding, so the standard-14 fonts render it. */
    static final char NBSP = ' ';

    /** Where a French artisan reads a timestamp. UTC on a devis would be wrong by an hour or two. */
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private FrenchFormat() {}

    /** {@code 1 234,56 €} — grouped, decimal comma, non-breaking space before the sign. */
    static String money(MoneyEur amount) {
        return group(amount.render()) + NBSP + "€";
    }

    /** {@code 5,50 %} — the rate as written on a devis line. */
    static String percentage(Percentage rate) {
        return rate.render().replace('.', ',') + NBSP + "%";
    }

    /** {@code 22/08/2026}. */
    static String date(EffectiveDate date) {
        return pad(date.getDay()) + "/" + pad(date.getMonth()) + "/" + date.getYear();
    }

    /**
     * {@code 22/08/2026 à 16:30} — a validation act, in the artisan's own time zone.
     *
     * <p>{@code InstantUtc} deliberately has no {@code render()}: M1 recorded that formatting a
     * moment needs a locale *and* a zone, both of which are adapter knowledge. This is that adapter,
     * and choosing the zone is part of the job rather than something to leave to the platform.
     */
    static String instant(long epochMilliseconds) {
        ZonedDateTime moment = Instant.ofEpochMilli(epochMilliseconds).atZone(PARIS);
        return pad(moment.getDayOfMonth())
                + "/"
                + pad(moment.getMonthValue())
                + "/"
                + moment.getYear()
                + " à "
                + pad(moment.getHour())
                + ":"
                + pad(moment.getMinute());
    }

    /** {@code 19.032} kW as {@code 19,032 kW}. Units keep their French forms (§13). */
    static String withUnit(String canonical, String unit) {
        return canonical.replace('.', ',') + NBSP + unit;
    }

    /** Groups the integer part in threes and swaps the decimal point, leaving any sign alone. */
    private static String group(String canonical) {
        boolean negative = canonical.startsWith("-");
        String unsigned = negative ? canonical.substring(1) : canonical;

        int decimalPoint = unsigned.indexOf('.');
        String integerPart = decimalPoint < 0 ? unsigned : unsigned.substring(0, decimalPoint);
        String fraction = decimalPoint < 0 ? "" : "," + unsigned.substring(decimalPoint + 1);

        StringBuilder grouped = new StringBuilder();
        int digitsUntilBreak = integerPart.length() % 3;
        for (int position = 0; position < integerPart.length(); position++) {
            if (position > 0 && (position - digitsUntilBreak) % 3 == 0) {
                grouped.append(NBSP);
            }
            grouped.append(integerPart.charAt(position));
        }
        return (negative ? "-" : "") + grouped + fraction;
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
