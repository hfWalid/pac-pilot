import { identifyCore } from '../core-bridge';

/**
 * Landing page for M0-04.
 *
 * Its only job is to prove the two-target bet end to end: the value below is computed by the
 * Kotlin core's JS target, not hardcoded here. Change `CoreInfo.identify()` in `commonMain`,
 * rebuild, and this text changes.
 *
 * Real screens arrive at M7.
 */
export function App() {
  return (
    <main>
      <h1>pac-pilot</h1>
      <p>
        Core linked: <strong data-testid="core-id">{identifyCore()}</strong>
      </p>
      <p>
        Computed by the Kotlin Multiplatform core (JS target) — the same source the server re-runs
        to verify.
      </p>
    </main>
  );
}
