// Minimal service worker — REGISTRATION ONLY (M0-04).
//
// It deliberately does not cache. The offline strategy is M7's work: precaching the app shell,
// serving it cache-first, and coordinating with the IndexedDB store and the outbox. Shipping a
// half-considered caching policy now would be worse than none, because a stale shell served to an
// installer in a cellar is exactly the failure this product cannot afford.
//
// The fetch handler is a pass-through so the worker is active and installability criteria are met
// without changing any network behaviour.

self.addEventListener('install', () => {
  // Take over immediately; there is no previous version to drain.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', () => {
  // Intentionally empty: let the network handle everything until M7.
});
