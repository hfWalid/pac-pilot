package fr.pacpilot.server.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Entry point of the modular monolith.
 *
 * Lives in `platform` — the cross-cutting package. Component scanning starts here and reaches every
 * bounded context under `fr.pacpilot.server`, but contexts must not reach into one another:
 * that wall is enforced by ArchUnit at M4.
 */
@SpringBootApplication(scanBasePackages = ["fr.pacpilot.server"])
class PacPilotApplication

fun main(args: Array<String>) {
    runApplication<PacPilotApplication>(*args)
}
