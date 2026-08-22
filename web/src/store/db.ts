// The device's replica. Everything the installer creates lives here first and syncs later
// (CLAUDE.md §4.3, §8) — the network is the optional part, not the storage.

/** Bumped only when the schema changes; the upgrade path below must handle every older version. */
const VERSION = 1;
const NAME = 'pac-pilot';

/**
 * One store per aggregate, plus the outbox and the pack cache.
 *
 * Keyed by the aggregate's own **client-generated UUID**. The database never mints an id — an
 * aggregate is born offline in a cellar, and its identity has to be stable from that moment, before
 * any server has heard of it.
 */
export const STORES = {
  clients: 'clients',
  sites: 'sites',
  dimensionings: 'dimensionings',
  quotes: 'quotes',
  interventions: 'interventions',
  photos: 'photos',
  rulepacks: 'rulepacks',
  outbox: 'outbox',
} as const;

export type StoreName = (typeof STORES)[keyof typeof STORES];

let connection: Promise<IDBDatabase> | null = null;

export function openDatabase(): Promise<IDBDatabase> {
  if (connection) return connection;

  connection = new Promise((resolve, reject) => {
    const request = indexedDB.open(NAME, VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;

      for (const store of Object.values(STORES)) {
        if (db.objectStoreNames.contains(store)) continue;

        if (store === STORES.outbox) {
          // The one store the device assigns keys for: outbox entries are events in order, not
          // aggregates, and their order is what makes replay deterministic (§8).
          db.createObjectStore(store, { keyPath: 'sequence', autoIncrement: true });
        } else {
          db.createObjectStore(store, { keyPath: 'id' });
        }
      }

      // Sites belong to a client and interventions to a site; the timeline and the dossier screen
      // both read by parent, and a full scan on a device with a year of visits is not free.
      const transaction = request.transaction!;
      index(transaction, STORES.sites, 'clientId');
      index(transaction, STORES.dimensionings, 'siteId');
      index(transaction, STORES.interventions, 'siteId');
      index(transaction, STORES.photos, 'dimensioningId');
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });

  return connection;
}

function index(transaction: IDBTransaction, store: StoreName, keyPath: string): void {
  const objectStore = transaction.objectStore(store);
  if (!objectStore.indexNames.contains(keyPath)) {
    objectStore.createIndex(keyPath, keyPath, { unique: false });
  }
}

/** Test seam. Production never calls this. */
export function resetConnectionForTests(): void {
  connection = null;
}

function run<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function put<T>(store: StoreName, value: T): Promise<T> {
  const db = await openDatabase();
  const transaction = db.transaction(store, 'readwrite');
  await run(transaction.objectStore(store).put(value));
  await completed(transaction);
  return value;
}

export async function get<T>(store: StoreName, id: string): Promise<T | undefined> {
  const db = await openDatabase();
  return run<T | undefined>(db.transaction(store, 'readonly').objectStore(store).get(id));
}

export async function all<T>(store: StoreName): Promise<T[]> {
  const db = await openDatabase();
  return run<T[]>(db.transaction(store, 'readonly').objectStore(store).getAll());
}

export async function allBy<T>(store: StoreName, indexName: string, key: string): Promise<T[]> {
  const db = await openDatabase();
  const objectStore = db.transaction(store, 'readonly').objectStore(store);
  return run<T[]>(objectStore.index(indexName).getAll(key));
}

export async function append<T>(store: StoreName, value: T): Promise<void> {
  const db = await openDatabase();
  const transaction = db.transaction(store, 'readwrite');
  await run(transaction.objectStore(store).add(value as never));
  await completed(transaction);
}

function completed(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

/** Client-generated, at creation, offline. */
export function newId(): string {
  return crypto.randomUUID();
}
