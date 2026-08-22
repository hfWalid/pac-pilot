package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRule;
import fr.pacpilot.core.aids.model.AidRuleId;
import fr.pacpilot.core.aids.model.AidRulePackVersion;
import fr.pacpilot.core.aids.model.IncomeDecile;
import fr.pacpilot.core.aids.model.VatRate;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Parses the encoded barème format described in {@code rulepacks/sources/README.md}.
 *
 * <p>Deliberately tiny and dependency-free, for the same reason the golden-vector parser is: the
 * file's real audience is a person holding an {@code anah.gouv.fr} page beside it, and a format that
 * needs a library to read is a format that has drifted away from that reader.
 *
 * <p><b>Every failure names the file and the line.</b> These messages are read by someone
 * mid-publication with a barème open; "malformed source" would send them to the code.
 */
public final class PackSourceParser {

    private PackSourceParser() {}

    public static PackSource parse(String content, String origin) {
        Map<String, String> header = new LinkedHashMap<>();
        List<Section> sections = new ArrayList<>();
        Section current = null;

        int lineNumber = 0;
        for (String raw : content.split("\n", -1)) {
            lineNumber++;
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                current = new Section(line.substring(1, line.length() - 1).trim(), lineNumber);
                sections.add(current);
                continue;
            }

            int equals = line.indexOf('=');
            if (equals < 0) {
                throw new PackSourceException(origin, lineNumber, "expected 'key = value', got '" + line + "'");
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();

            if (current == null) {
                header.put(key, value);
            } else if (current.entries.put(key, value) != null) {
                throw new PackSourceException(origin, lineNumber, "duplicate key '" + key + "' in [" + current.name + "]");
            }
        }

        return assemble(header, sections, origin);
    }

    private static PackSource assemble(Map<String, String> header, List<Section> sections, String origin) {
        AidRulePackVersion version = new AidRulePackVersion(required(header, "version", origin));
        EffectiveDate from = date(required(header, "effective-from", origin), origin);
        Optional<EffectiveDate> to =
                Optional.ofNullable(header.get("effective-to"))
                        .filter(value -> !value.isBlank())
                        .map(value -> date(value, origin));

        VatRate vatRate = null;
        List<AidRule> aids = new ArrayList<>();

        for (Section section : sections) {
            switch (section.name) {
                case "vat" -> vatRate = vat(section, origin);
                case "aid income-tiered" -> aids.add(incomeTiered(section, origin));
                case "aid forfait" -> aids.add(forfait(section, origin));
                case "aid rate-based" -> aids.add(rateBased(section, origin));
                default ->
                        throw new PackSourceException(
                                origin,
                                section.line,
                                "unknown section '[" + section.name + "]'; expected [vat], "
                                        + "[aid income-tiered], [aid forfait] or [aid rate-based]");
            }
        }

        if (vatRate == null) {
            throw new PackSourceException(origin, 0, "no [vat] section; every pack states the rate in force");
        }
        return new PackSource(version, from, to, vatRate, aids, origin);
    }

    private static VatRate vat(Section section, String origin) {
        return new VatRate(percentage(section, "rate", origin), required(section, "source", origin));
    }

    private static AidRule incomeTiered(Section section, String origin) {
        Map<IncomeDecile, MoneyEur> byDecile = new TreeMap<>(java.util.Comparator.comparingInt(IncomeDecile::getValue));
        section.entries.forEach(
                (key, value) -> {
                    if (key.startsWith("decile.")) {
                        int decile = Integer.parseInt(key.substring("decile.".length()));
                        byDecile.put(new IncomeDecile(decile), euros(value, section, key, origin));
                    }
                });
        if (byDecile.isEmpty()) {
            throw new PackSourceException(
                    origin, section.line, "[aid income-tiered] has no decile.N entries; it pays nobody");
        }
        return new AidRule.IncomeTiered(
                new AidRuleId(required(section, "id", origin)),
                required(section, "label", origin),
                required(section, "source", origin),
                byDecile,
                optionalEuros(section, "cap", origin));
    }

    private static AidRule forfait(Section section, String origin) {
        return new AidRule.Forfait(
                new AidRuleId(required(section, "id", origin)),
                required(section, "label", origin),
                required(section, "source", origin),
                euros(required(section, "amount", origin), section, "amount", origin));
    }

    private static AidRule rateBased(Section section, String origin) {
        return new AidRule.RateBased(
                new AidRuleId(required(section, "id", origin)),
                required(section, "label", origin),
                required(section, "source", origin),
                percentage(section, "rate", origin),
                optionalEuros(section, "cap", origin));
    }

    /** Percent with two decimals to basis points — {@code 5.50} becomes {@code 550}, exactly. */
    private static Percentage percentage(Section section, String key, String origin) {
        String value = required(section, key, origin);
        try {
            return new Percentage(new BigDecimal(value).movePointRight(2).intValueExact());
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new PackSourceException(
                    origin, section.line, "'" + key + " = " + value + "' is not a percent with at most two decimals");
        }
    }

    /** Euros with two decimals to cents. No floating point anywhere on this path. */
    private static MoneyEur euros(String value, Section section, String key, String origin) {
        try {
            return new MoneyEur(new BigDecimal(value).movePointRight(2).longValueExact());
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new PackSourceException(
                    origin, section.line, "'" + key + " = " + value + "' is not an amount in euros with at most two decimals");
        }
    }

    private static MoneyEur optionalEuros(Section section, String key, String origin) {
        String value = section.entries.get(key);
        return value == null || value.isBlank() ? null : euros(value, section, key, origin);
    }

    private static EffectiveDate date(String value, String origin) {
        String[] parts = value.split("-");
        if (parts.length != 3) {
            throw new PackSourceException(origin, 0, "'" + value + "' is not a date in YYYY-MM-DD form");
        }
        return new EffectiveDate(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String required(Map<String, String> entries, String key, String origin) {
        String value = entries.get(key);
        if (value == null || value.isBlank()) {
            throw new PackSourceException(origin, 0, "missing '" + key + "'");
        }
        return value;
    }

    private static String required(Section section, String key, String origin) {
        String value = section.entries.get(key);
        if (value == null || value.isBlank()) {
            throw new PackSourceException(
                    origin, section.line, "[" + section.name + "] is missing '" + key + "'");
        }
        return value;
    }

    private static final class Section {
        private final String name;
        private final int line;
        private final Map<String, String> entries = new LinkedHashMap<>();

        private Section(String name, int line) {
            this.name = name;
            this.line = line;
        }
    }
}
