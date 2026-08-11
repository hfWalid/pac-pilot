package fr.pacpilot.server.platform.adapter.`in`.web

import fr.pacpilot.core.CoreInfo
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Liveness probe. Consumed by CI, docker-compose and — from M11 — the FR load balancer.
 *
 * It reports the core it is linked against, which makes the server/core wiring observable at
 * runtime rather than only at build time.
 */
@RestController
class HealthController {

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "UP", "core" to CoreInfo.identify()))
}
