package fr.pacpilot.server.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the modular monolith.
 *
 * <p>Lives in {@code platform} — the cross-cutting package. Component scanning starts here and
 * reaches every bounded context under {@code fr.pacpilot.server}, but contexts must not reach into
 * one another: that wall is enforced by ArchUnit at M4.
 */
@SpringBootApplication(scanBasePackages = "fr.pacpilot.server")
public class PacPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacPilotApplication.class, args);
    }
}
