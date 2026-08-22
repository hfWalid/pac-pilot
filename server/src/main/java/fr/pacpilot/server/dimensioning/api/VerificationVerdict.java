package fr.pacpilot.server.dimensioning.api;

import java.util.List;

/**
 * What recomputing a stored study on the server concluded ({@code CLAUDE.md} §4.2).
 *
 * <p>Sealed and three-way. The third case is the one that matters most: {@link NotVerifiable} is not
 * a failure and not a pass — it is the honest answer when there is nothing to verify against, which
 * is the normal state today while the method gate (PAC-42) is open. Collapsing it into
 * {@link Matched} would report an unverified result as verified, and that is the single outcome this
 * product cannot produce.
 *
 * <p><b>There is no fourth case for "corrected".</b> A divergence is persisted and surfaced, never
 * repaired: the stored result is what an installer signed, and a server that quietly rewrote it
 * would be replacing evidence rather than checking it.
 */
public sealed interface VerificationVerdict {

    /** Recomputation reproduced the stored result exactly. */
    record Matched() implements VerificationVerdict {}

    /**
     * Recomputation disagreed. [differences] names every field that differs, with both values —
     * "mismatch" sends someone to a debugger, "heatLoad: stored 19.032, recomputed 19.031" sends
     * them to the formula set.
     */
    record Diverged(List<FieldDifference> differences) implements VerificationVerdict {
        public Diverged {
            if (differences == null || differences.isEmpty()) {
                throw new IllegalArgumentException("a divergence names at least one differing field");
            }
            differences = List.copyOf(differences);
        }

        public String render() {
            return differences.stream().map(FieldDifference::render).reduce((a, b) -> a + "; " + b).orElseThrow();
        }
    }

    /** Nothing to verify against — no validated method in force, or no barème pack for the date. */
    record NotVerifiable(String reason) implements VerificationVerdict {
        public NotVerifiable {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("an unverifiable result says why");
            }
        }
    }

    /** One field that disagreed, with both rendered values. Rendered, never floating-point. */
    record FieldDifference(String field, String stored, String recomputed) {
        public String render() {
            return field + ": stored " + stored + ", recomputed " + recomputed;
        }
    }
}
