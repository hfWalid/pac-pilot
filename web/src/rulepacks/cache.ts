// Pulling, caching and verifying barème packs on the device (M6-06's contract, PAC-73).
//
// The installer must have the barème *before* going into a cellar, know it is genuine, and be told
// when it is out of date — without any of that blocking the visit.

import { STORES, all, put } from '../store/db';

/**
 * The three states, and they must stay distinguishable.
 *
 * They mean different things to the installer, and collapsing them into one error loses exactly the
 * information the banner needs:
 *
 * - `fresh` — a verified pack covers the devis date.
 * - `stale` — a verified pack exists but its range has passed. **Still computes**, marked provisional.
 * - `missing` — nothing covers the date. Refuses; this is the engine's `NO_PACK_PUBLISHED`.
 * - `tampered` — a cached pack failed checksum verification. Refuses, **visibly**, and is never used.
 */
export type PackState =
  | { state: 'fresh'; version: string }
  | { state: 'stale'; version: string; endedOn: string }
  | { state: 'missing' }
  | { state: 'tampered'; version: string };

interface CachedPack {
  id: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  checksum: string;
  signature: string;
  /** The published artefact verbatim, so verification hashes exactly what was published. */
  content: string;
}

/**
 * SHA-256 of the pack's canonical form, computed with the platform's own primitive.
 *
 * The canonical *rendering* lives in `:core` (`AidRulePackCanonicalForm`) so the device hashes
 * exactly what the pipeline hashed. Only the hash is platform-specific — the same split the domain
 * makes for signatures: the domain describes, the platform computes.
 */
async function sha256(canonical: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical));
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

/**
 * Verifies and caches one published pack.
 *
 * **A pack that fails verification is rejected and never cached.** Not cached-with-a-warning, and
 * emphatically not replaced by an older pack: silently falling back would turn a tampering signal
 * into a slightly-wrong devis, and the whole point of the checksum is that somebody finds out.
 */
export async function cachePack(
  content: string,
  canonicalForm: string,
  meta: { version: string; effectiveFrom: string; effectiveTo: string | null; checksum: string; signature: string },
): Promise<'cached' | 'rejected'> {
  const recomputed = await sha256(canonicalForm);
  if (recomputed !== meta.checksum) {
    return 'rejected';
  }

  const cached: CachedPack = {
    id: meta.version,
    effectiveFrom: meta.effectiveFrom,
    effectiveTo: meta.effectiveTo,
    checksum: meta.checksum,
    signature: meta.signature,
    content,
  };
  await put(STORES.rulepacks, cached);
  return 'cached';
}

/**
 * What the device can say about the barème for a given date.
 *
 * `effectiveTo` is **inclusive** (M1-07) — a barème "applicable jusqu'au 30 juin" ends on the 30th.
 * The comparison is on ISO date strings, which sort correctly precisely because `EffectiveDate`
 * renders zero-padded.
 */
export async function packStateOn(isoDate: string): Promise<PackState> {
  const cached = await all<CachedPack>(STORES.rulepacks);
  if (cached.length === 0) return { state: 'missing' };

  const covering = cached.find(
    (pack) => isoDate >= pack.effectiveFrom && (pack.effectiveTo === null || isoDate <= pack.effectiveTo),
  );
  if (covering) return { state: 'fresh', version: covering.id };

  // Nothing covers the date, but something is cached. If the newest cached pack ended before this
  // date, the device is simply behind — which is a different message from having no barème at all,
  // and the difference is what the banner in PRODUCT-VIEWS #9 is for.
  const newest = cached
    .filter((pack) => pack.effectiveTo !== null && pack.effectiveTo < isoDate)
    .sort((a, b) => (a.effectiveTo! < b.effectiveTo! ? 1 : -1))[0];

  return newest ? { state: 'stale', version: newest.id, endedOn: newest.effectiveTo! } : { state: 'missing' };
}

/**
 * Pulls published packs from the server.
 *
 * Opportunistic by design: it is called when the app opens, never when a devis is being priced.
 * iOS can evict stored data after weeks of disuse (ADR-0003), so an installer returning after a
 * fortnight may find the cache empty — that is the `missing` state, not an error, and prompting a
 * sync on open is what keeps it rare.
 */
export async function pullPacks(baseUrl: string): Promise<{ pulled: number; rejected: number }> {
  const response = await fetch(`${baseUrl}/api/rulepacks`, { headers: { accept: 'application/json' } });
  if (!response.ok) return { pulled: 0, rejected: 0 };

  const packs: {
    version: string;
    effectiveFrom: string;
    effectiveTo: string | null;
    checksum: string;
    signature: string;
    content: string;
    canonicalForm: string;
  }[] = await response.json();

  let pulled = 0;
  let rejected = 0;
  for (const pack of packs) {
    const outcome = await cachePack(pack.content, pack.canonicalForm, pack);
    outcome === 'cached' ? pulled++ : rejected++;
  }
  return { pulled, rejected };
}
