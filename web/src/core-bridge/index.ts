// The single point where the PWA talks to the Kotlin Multiplatform core.
//
// Everything the UI computes must come through here, so that the JS target of the core is the only
// calculation path on the device — the same source the server later re-runs to verify (ARCHITECTURE #3).
// Feature code must never import the Kotlin artifact directly; keeping it behind this module is what
// lets M7 swap the import shape without touching screens.
//
// Kotlin's generated declarations are awkward to consume directly (objects arrive as
// `KtSingleton`-wrapped classes), so this module re-exposes them as plain, typed functions.

import { fr } from 'pac-pilot-core';

const core = fr.pacpilot.core;

/** Identifies the core build the PWA is linked against. Placeholder until M1 lands the real model. */
export function identifyCore(): string {
  return core.CoreFacade.identify();
}
