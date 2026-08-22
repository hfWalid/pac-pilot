// Per-aggregate repositories over the device replica.
//
// Every write records an outbox entry in the same call, because a write that reached storage but not
// the outbox is a change the server will never hear about — and nothing later could detect that it
// had been lost.

import { STORES, all, allBy, get, newId, put } from './db';
import { record } from '../outbox/outbox';
import type {
  ClientRecord,
  DimensioningRecord,
  PhotoRecord,
  QuoteRecord,
  SiteRecord,
} from './records';

const now = () => new Date().toISOString();

export async function saveClient(client: Omit<ClientRecord, 'id' | 'recordedAt'> & { id?: string }) {
  const stored: ClientRecord = { ...client, id: client.id ?? newId(), recordedAt: now() };
  await put(STORES.clients, stored);
  await record('client.recorded', stored.id, stored);
  return stored;
}

export async function saveSite(site: Omit<SiteRecord, 'id' | 'recordedAt'> & { id?: string }) {
  const stored: SiteRecord = { ...site, id: site.id ?? newId(), recordedAt: now() };
  await put(STORES.sites, stored);
  await record('site.recorded', stored.id, stored);
  return stored;
}

export async function saveDimensioning(
  study: Omit<DimensioningRecord, 'id' | 'recordedAt'> & { id?: string },
) {
  const stored: DimensioningRecord = { ...study, id: study.id ?? newId(), recordedAt: now() };
  await put(STORES.dimensionings, stored);
  await record('dimensioning.computed', stored.id, stored);
  return stored;
}

/**
 * The validation act — a distinct operation, never a field update.
 *
 * `ARCHITECTURE` #7 allows only `Validated → Quoted`, and re-signing is not refused at runtime here
 * so much as never offered: a study that already carries a signature is returned untouched, because
 * re-deciding means computing a new study under a new id, which is an addition to the evidential
 * record rather than an edit of it.
 */
export async function validateDimensioning(id: string, installerId: string) {
  const study = await get<DimensioningRecord>(STORES.dimensionings, id);
  if (!study) throw new Error(`no study ${id} to validate`);
  if (study.validatedBy) return study;

  const validated: DimensioningRecord = {
    ...study,
    validatedBy: installerId,
    validatedAt: now(),
  };
  await put(STORES.dimensionings, validated);
  await record('dimensioning.validated', id, {
    validatedBy: validated.validatedBy,
    validatedAt: validated.validatedAt,
  });
  return validated;
}

export async function saveQuote(quote: Omit<QuoteRecord, 'id' | 'recordedAt'> & { id?: string }) {
  const stored: QuoteRecord = { ...quote, id: quote.id ?? newId(), recordedAt: now() };
  await put(STORES.quotes, stored);
  await record('quote.drafted', stored.id, stored);
  return stored;
}

export async function savePhoto(photo: Omit<PhotoRecord, 'id' | 'uploaded'> & { id?: string }) {
  const stored: PhotoRecord = { ...photo, id: photo.id ?? newId(), uploaded: false };
  await put(STORES.photos, stored);
  // The blob is deliberately not in the outbox payload: M9 uploads the bytes separately, and an
  // outbox entry carrying megabytes would make replay a memory problem.
  await record('photo.captured', stored.id, {
    dimensioningId: stored.dimensioningId,
    capturedAt: stored.capturedAt,
    latitude: stored.latitude,
    longitude: stored.longitude,
  });
  return stored;
}

export const findClient = (id: string) => get<ClientRecord>(STORES.clients, id);
export const findSite = (id: string) => get<SiteRecord>(STORES.sites, id);
export const findDimensioning = (id: string) => get<DimensioningRecord>(STORES.dimensionings, id);
export const listClients = () => all<ClientRecord>(STORES.clients);
export const sitesOfClient = (clientId: string) => allBy<SiteRecord>(STORES.sites, 'clientId', clientId);
export const photosOfStudy = (dimensioningId: string) =>
  allBy<PhotoRecord>(STORES.photos, 'dimensioningId', dimensioningId);
