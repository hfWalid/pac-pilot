import { beforeEach, describe, expect, it } from 'vitest';
import { STORES, all, resetConnectionForTests } from './db';
import {
  findDimensioning,
  saveClient,
  saveDimensioning,
  saveSite,
  validateDimensioning,
} from './repositories';
import { pending } from '../outbox/outbox';
import type { DimensioningRecord } from './records';

const survey = {
  surfaceCentiM2: 12_000,
  ceilingHeightCm: 250,
  constructionPeriod: 'BEFORE_1975',
  insulationLevel: 'PARTIAL',
  ventilationType: 'VMC_SIMPLE_FLUX',
  emitterType: 'RADIATOR_HIGH_TEMPERATURE',
  climateZone: 'H1',
  baseTemperatureDeciC: -70,
  targetIndoorTemperatureDeciC: 190,
  electricalSupplyKva: 9,
};

const result = {
  heatLoadWatts: 19_032,
  heatLoadKw: '19.032',
  powerBandMinimumWatts: 17_129,
  powerBandMaximumWatts: 22_838,
  flowTemperatureC: '50.0',
  confidence: 'INDICATIVE',
  provisional: true,
  assumptions: [{ statement: 'U-value', source: 'SOURCE_TBD', provisional: true }],
};

const aStudy = () =>
  saveDimensioning({ siteId: 'site-1', inputs: survey, effectiveDate: '2026-08-22', result });

describe('the device replica', () => {
  beforeEach(() => {
    globalThis.indexedDB = new IDBFactory();
    resetConnectionForTests();
  });

  it('gives every aggregate a client-generated id, before any server has heard of it', async () => {
    const client = await saveClient({
      installerId: 'installer-1',
      firstName: 'Camille',
      lastName: 'Berthier',
    });

    expect(client.id).toMatch(/^[0-9a-f-]{36}$/);
    expect(await all(STORES.clients)).toHaveLength(1);
  });

  it('records an outbox entry for every write, in order', async () => {
    // A write that reached storage but not the outbox is a change the server never hears about,
    // and nothing later could detect that it had been lost.
    const client = await saveClient({ installerId: 'i', firstName: 'Camille', lastName: 'Berthier' });
    await saveSite({
      clientId: client.id,
      addressLine: '12 rue des Lilas',
      postcode: '69003',
      commune: 'Lyon',
      departementCode: '69',
    });

    const entries = await pending();
    expect(entries.map((e) => e.kind)).toEqual(['client.recorded', 'site.recorded']);
    expect(entries[0].sequence!).toBeLessThan(entries[1].sequence!);
  });

  it('stores the engine result verbatim, so the server can verify what the device showed', async () => {
    const study = await aStudy();

    const reloaded = (await findDimensioning(study.id)) as DimensioningRecord;
    expect(reloaded.result.heatLoadWatts).toBe(19_032);
    expect(reloaded.result.assumptions).toHaveLength(1);
    expect(reloaded.inputs.surfaceCentiM2).toBe(12_000);
  });

  it('leaves a fresh study unsigned — validation is an act, never a default', async () => {
    const study = await aStudy();

    expect(study.validatedBy).toBeUndefined();
    expect(study.validatedAt).toBeUndefined();
  });

  it('records the validation act separately from the computation', async () => {
    const study = await aStudy();

    const validated = await validateDimensioning(study.id, 'installer-42');

    expect(validated.validatedBy).toBe('installer-42');
    expect(validated.validatedAt).toBeDefined();
    expect((await pending()).map((e) => e.kind)).toContain('dimensioning.validated');
  });

  it('does not re-sign a study that already carries a signature', async () => {
    // Re-deciding means computing a new study under a new id — an addition to the evidential
    // record, never an edit of it.
    const study = await aStudy();
    const first = await validateDimensioning(study.id, 'installer-42');
    const again = await validateDimensioning(study.id, 'someone-else');

    expect(again.validatedBy).toBe('installer-42');
    expect(again.validatedAt).toBe(first.validatedAt);
  });
});
