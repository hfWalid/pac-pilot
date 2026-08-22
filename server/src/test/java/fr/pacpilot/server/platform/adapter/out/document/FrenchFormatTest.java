package fr.pacpilot.server.platform.adapter.out.document;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FrenchFormatTest {

    private static final String NBSP = " ";

    @Test
    void moneyIsGroupedWithNonBreakingSpacesAndADecimalComma() {
        assertThat(FrenchFormat.money(MoneyEur.Companion.ofEuros(1_234))).isEqualTo("1" + NBSP + "234,00" + NBSP + "€");
        assertThat(FrenchFormat.money(new MoneyEur(123_456))).isEqualTo("1" + NBSP + "234,56" + NBSP + "€");
        assertThat(FrenchFormat.money(new MoneyEur(56))).isEqualTo("0,56" + NBSP + "€");
        assertThat(FrenchFormat.money(MoneyEur.Companion.ofEuros(14_770))).isEqualTo("14" + NBSP + "770,00" + NBSP + "€");
    }

    @Test
    void aMillionGroupsTwice() {
        assertThat(FrenchFormat.money(MoneyEur.Companion.ofEuros(1_234_567)))
                .isEqualTo("1" + NBSP + "234" + NBSP + "567,00" + NBSP + "€");
    }

    @Test
    void anOverGrantedResteAChargeKeepsItsSignInFront() {
        // ResteACharge is deliberately not clamped at zero (M1-07): aids exceeding the cost mean a
        // barème was misapplied, and the page must be able to show that rather than hide it.
        assertThat(FrenchFormat.money(new MoneyEur(-944_000)))
                .isEqualTo("-9" + NBSP + "440,00" + NBSP + "€");
    }

    @Test
    void ratesAndDatesFollowTheFrenchConvention() {
        assertThat(FrenchFormat.percentage(new Percentage(550))).isEqualTo("5,50" + NBSP + "%");
        assertThat(FrenchFormat.date(new EffectiveDate(2026, 8, 22))).isEqualTo("22/08/2026");
        assertThat(FrenchFormat.date(new EffectiveDate(2026, 1, 3))).isEqualTo("03/01/2026");
    }

    @Test
    void aValidationInstantIsShownInTheArtisansOwnTimeZone() {
        // 14:30 UTC on a summer day is 16:30 in Lyon. Showing UTC to a French artisan is wrong by
        // an hour or two, which on a signature timestamp is not a rounding detail.
        long summer = Instant.parse("2026-08-22T14:30:00Z").toEpochMilli();
        assertThat(FrenchFormat.instant(summer)).isEqualTo("22/08/2026 à 16:30");

        long winter = Instant.parse("2026-01-15T14:30:00Z").toEpochMilli();
        assertThat(FrenchFormat.instant(winter)).isEqualTo("15/01/2026 à 15:30");
    }

    @Test
    void unitsKeepTheirCanonicalPrecisionWithAFrenchDecimalComma() {
        assertThat(FrenchFormat.withUnit("19.032", "kW")).isEqualTo("19,032" + NBSP + "kW");
        assertThat(FrenchFormat.withUnit("-7.0", "°C")).isEqualTo("-7,0" + NBSP + "°C");
    }
}
