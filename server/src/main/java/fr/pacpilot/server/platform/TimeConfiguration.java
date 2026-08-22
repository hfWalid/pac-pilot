package fr.pacpilot.server.platform;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one place wall-clock time enters the application.
 *
 * <p>{@code :core} has no clock at all, by design and enforced by ArchUnit ({@code CLAUDE.md} §10):
 * a study or a devis recomputed years later must apply the method and the barème in force on its
 * own date, so every domain operation takes the date as a parameter. Nothing about that changes
 * here.
 *
 * <p>What the server still needs is record-keeping time — when a row was first written — which is a
 * persistence fact rather than a domain one. Injecting a {@link Clock} rather than calling
 * {@code Instant.now()} keeps that boundary visible: a class that needs the current time has to
 * declare it in its constructor, so the ones that quietly reach for it are the ones that stand out
 * in review. It also makes those classes testable with a fixed clock instead of a tolerance.
 *
 * <p>UTC, not the system zone. Storage is {@code timestamptz} throughout and rendering in French
 * local time is the PDF adapter's job at M5 — a server that stored local time would make every
 * timestamp ambiguous twice a year.
 */
@Configuration
class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
