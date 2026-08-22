package fr.pacpilot.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the modular monolith.
 *
 * <p><b>At the root of the scanned package on purpose, since M4-03.</b> It sat in {@code platform}
 * with {@code scanBasePackages = "fr.pacpilot.server"}, which looks equivalent and is not:
 * {@code scanBasePackages} directs <i>component</i> scanning only. Spring Data's repository
 * scanning and Hibernate's entity scanning both derive from {@code AutoConfigurationPackages},
 * which is computed from this class's own package — so with the class in {@code platform}, no
 * repository or entity in any other context was ever found. The first persistence adapter failed
 * with a missing-bean error that pointed at the adapter rather than at the cause.
 *
 * <p>Moving it here fixes repository scanning, entity scanning, and the package search
 * {@code @SpringBootTest} performs, all of which want the same root. {@code platform} keeps the
 * cross-cutting concerns; it no longer holds the bootstrap.
 *
 * <p>Component scanning reaches every bounded context under {@code fr.pacpilot.server}, but
 * contexts must not reach into one another — that wall is {@code BoundedContextRulesTest}'s.
 */
@SpringBootApplication
public class PacPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacPilotApplication.class, args);
    }
}
