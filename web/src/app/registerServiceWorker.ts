/**
 * Registers the service worker so the app is installable to the home screen.
 *
 * The installer adds the app once from a URL — there is no app store and no native build
 * (CLAUDE.md §2). Registration failure must never break the page: the app still works online,
 * and M7 owns the real offline behaviour.
 */
export function registerServiceWorker(): void {
  if (!('serviceWorker' in navigator)) {
    return;
  }

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((error: unknown) => {
      console.warn('Service worker registration failed', error);
    });
  });
}
