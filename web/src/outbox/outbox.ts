// The append-only change log (CLAUDE.md §8).
//
// Every local write records what changed, in order, so a reconnecting device can replay it at the
// server. M8 owns the sending; M7 owns the recording — and recording from the first write is what
// makes M8 an integration problem rather than an archaeology one, since a change never captured
// cannot be reconstructed afterwards.

import { STORES, all, append } from '../store/db';

export type OutboxKind =
  | 'client.recorded'
  | 'site.recorded'
  | 'dimensioning.computed'
  | 'dimensioning.validated'
  | 'quote.drafted'
  | 'photo.captured'
  | 'intervention.recorded';

export interface OutboxEntry<T = unknown> {
  /** Assigned by IndexedDB. Order is the whole point: replay must follow it. */
  sequence?: number;
  kind: OutboxKind;
  /** The aggregate this concerns, so the server can make ingestion idempotent (§8). */
  aggregateId: string;
  payload: T;
  /** When the device recorded it. Not authoritative — the server records its own arrival time. */
  recordedAt: string;
}

export async function record<T>(kind: OutboxKind, aggregateId: string, payload: T): Promise<void> {
  const entry: OutboxEntry<T> = {
    kind,
    aggregateId,
    payload,
    recordedAt: new Date().toISOString(),
  };
  await append(STORES.outbox, entry);
}

/** Everything waiting to be sent, oldest first. Read by M8's sync. */
export async function pending(): Promise<OutboxEntry[]> {
  const entries = await all<OutboxEntry>(STORES.outbox);
  return entries.sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0));
}
