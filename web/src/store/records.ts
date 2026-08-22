// The shapes the device stores.
//
// Deliberately **not** the core's domain types. The core's aggregates carry value classes and
// sealed hierarchies that do not survive `structuredClone` — which is what IndexedDB uses — and a
// stored object that cannot be read back is worse than one that was never the domain type. The
// bridge converts; these are what persist.

export interface ClientRecord {
  id: string;
  installerId: string;
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  recordedAt: string;
}

export interface SiteRecord {
  id: string;
  clientId: string;
  addressLine: string;
  postcode: string;
  commune: string;
  departementCode: string;
  latitude?: number;
  longitude?: number;
  recordedAt: string;
}

/** The survey: what the installer observed, in the exact minor units the engine consumes. */
export interface SurveyRecord {
  surfaceCentiM2: number;
  ceilingHeightCm: number;
  constructionPeriod: string;
  insulationLevel: string;
  ventilationType: string;
  emitterType: string;
  climateZone: string;
  baseTemperatureDeciC: number;
  targetIndoorTemperatureDeciC: number;
  electricalSupplyKva: number;
}

export interface DimensioningRecord {
  id: string;
  siteId: string;
  inputs: SurveyRecord;
  effectiveDate: string;
  /** The engine's own output, stored verbatim so the server can verify what the device showed. */
  result: {
    heatLoadWatts: number;
    heatLoadKw: string;
    powerBandMinimumWatts: number;
    powerBandMaximumWatts: number;
    flowTemperatureC: string | null;
    confidence: string;
    provisional: boolean;
    assumptions: { statement: string; source: string; provisional: boolean }[];
  };
  /** Set only by the validation act — the legal shield (§4.5). Never auto-filled. */
  validatedBy?: string;
  validatedAt?: string;
  recordedAt: string;
}

export interface QuoteRecord {
  id: string;
  dimensioningId: string;
  effectiveDate: string;
  productId: string;
  productModel: string;
  productPowerAtMinusSevenWatts: number;
  productPriceCents: number;
  lines: { label: string; unitPriceCents: number; quantity: number; vatBasisPoints: number }[];
  /** Absent while no barème is published — a refusal, never zero aids (ADR-0017). */
  aids?: {
    packVersion: string;
    lines: { rule: string; label: string; amountCents: number; source: string }[];
  };
  recordedAt: string;
}

/**
 * A pre-visit photo, with the metadata that makes it evidence.
 *
 * The timestamp and geotag are captured **at capture** (ADR-0020). Attaching them later — at upload,
 * from EXIF, on the server — would attach them from a less trustworthy source, and a geotag applied
 * after the fact is not evidence of where anyone stood.
 */
export interface PhotoRecord {
  id: string;
  dimensioningId: string;
  blob: Blob;
  capturedAt: string;
  latitude?: number;
  longitude?: number;
  /** True once M9 has uploaded it. Until then the device is the only copy. */
  uploaded: boolean;
}
