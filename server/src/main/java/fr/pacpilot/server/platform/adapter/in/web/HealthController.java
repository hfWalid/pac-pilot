package fr.pacpilot.server.platform.adapter.in.web;

import fr.pacpilot.core.CoreInfo;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe. Consumed by CI, docker-compose and — from M11 — the FR load balancer.
 *
 * <p>It reports the core it is linked against, which makes the server/core wiring observable at
 * runtime rather than only at build time. That reporting is now doing double duty: it is also the
 * simplest live proof that Java calls into the Kotlin Multiplatform core (ADR-0010).
 *
 * <p>{@code CoreInfo.INSTANCE} is how Java reaches a Kotlin {@code object}. It is deliberately left
 * visible rather than hidden behind a wrapper — the real domain surface arriving at M1 is classes
 * and interfaces, which Java consumes without ceremony, and hiding this now would disguise the one
 * interop constraint worth remembering.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "core", CoreInfo.INSTANCE.identify()));
    }
}
