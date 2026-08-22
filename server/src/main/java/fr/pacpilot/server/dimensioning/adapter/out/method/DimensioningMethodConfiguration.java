package fr.pacpilot.server.dimensioning.adapter.out.method;

import fr.pacpilot.core.dimensioning.engine.DimensioningEngine;
import fr.pacpilot.core.dimensioning.port.FormulaSetProvider;
import fr.pacpilot.core.dimensioning.port.RunDimensioning;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses which heat-loss method this deployment runs, and refuses to choose one for you.
 *
 * <p>This class is where ADR-0015 is enforced rather than merely described. The M2 gate worksheet
 * warned that a placeholder formula set "is exactly the artefact that quietly becomes authoritative
 * — wired into a demo, the demo becomes a pilot, and a {@code SOURCE_TBD} coefficient reaches a
 * homeowner." The decision to boot anyway was taken knowingly; these are the four properties that
 * keep it from happening <i>by accident</i>:
 *
 * <ol>
 *   <li><b>No default.</b> The {@code @Value} below has no fallback, so a missing property is a
 *       startup failure, not a silent choice.
 *   <li><b>It announces itself.</b> A {@code WARN} banner on every boot in indicative mode.
 *   <li><b>Every result says so.</b> {@code Confidence.INDICATIVE} is already derived from whether
 *       the applied coefficients are sourced (M2-06); nothing here special-cases it, and the
 *       provisional set cannot produce anything else.
 *   <li><b>The core stays clean.</b> The provisional set lives here, in {@code :server}, so
 *       {@code :core} still ships no placeholder and the PWA at M7 inherits none.
 * </ol>
 *
 * <p>None of that stops a determined operator who sets the property and ignores the banner. What
 * protects against that is PAC-42 closing.
 */
@Configuration
class DimensioningMethodConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DimensioningMethodConfiguration.class);

    static final String PROPERTY = "pacpilot.dimensioning.method";

    private final String configuredMethod;

    /**
     * @param configuredMethod bound from {@code pacpilot.dimensioning.method}. Deliberately no
     *     default value in the placeholder: Spring fails the context refresh when the property is
     *     absent, which is the behaviour ADR-0015 asks for.
     *     <p>Taken as a {@code String} and parsed in {@link #parse} rather than bound straight to
     *     the enum. Two reasons: {@code @Value} does not apply relaxed binding — that is
     *     {@code @ConfigurationProperties} — so {@code indicative-provisional} would not convert at
     *     all; and parsing here is what lets an unrecognised value fail with a message that lists
     *     what is actually accepted instead of a conversion stack trace.
     */
    DimensioningMethodConfiguration(@Value("${" + PROPERTY + "}") String configuredMethod) {
        this.configuredMethod = configuredMethod;
    }

    @Bean
    FormulaSetProvider formulaSetProvider() {
        return switch (parse(configuredMethod)) {
            case INDICATIVE_PROVISIONAL -> {
                warnLoudly();
                yield new ProvisionalFormulaSetProvider();
            }
            case VALIDATED ->
                    throw new IllegalStateException(
                            PROPERTY
                                    + "=validated, but no validated method exists yet. The simplified"
                                    + " EN 12831 method is still behind its human gate (PAC-42); until it"
                                    + " closes there is nothing to select. Set "
                                    + PROPERTY
                                    + "=indicative-provisional to run with placeholders whose results"
                                    + " are marked INDICATIVE and must not be shown to a client.");
        };
    }

    /** The driving port, wired to whichever method the property selected. */
    @Bean
    RunDimensioning runDimensioning(FormulaSetProvider formulaSetProvider) {
        return new DimensioningEngine(formulaSetProvider);
    }

    /**
     * Accepts the kebab-case an operator actually writes in a YAML file or an environment variable,
     * and refuses anything else by name.
     *
     * <p>No fallback branch. A typo must not select the safest-looking option — that is how a
     * deployment ends up in indicative mode without anyone deciding it should be.
     */
    private static DimensioningMethod parse(String configured) {
        String normalised = configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(DimensioningMethod.values())
                .filter(candidate -> candidate.name().equals(normalised))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        PROPERTY
                                                + "='"
                                                + configured
                                                + "' is not a method this server knows. Accepted values: "
                                                + Arrays.stream(DimensioningMethod.values())
                                                        .map(value -> value.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                                                        .collect(Collectors.joining(", "))
                                                + ". See ADR-0015."));
    }

    private void warnLoudly() {
        LOG.warn(
                """

                ════════════════════════════════════════════════════════════════════════════
                  DIMENSIONING METHOD: INDICATIVE — NO VALIDATED METHOD IS IN FORCE
                  Every coefficient is SOURCE_TBD and deliberately non-physical. U-values
                  rise with newer construction; air's heat capacity is 1.000, not ~0.34.
                  Heat loads produced here are arithmetic, not a study, and MUST NOT be
                  shown to a client or put on a devis.
                  Closing the gate: PAC-42. See ADR-0015 and docs/M2-GATE-WORKSHEET.md.
                ════════════════════════════════════════════════════════════════════════════
                """);
    }
}
