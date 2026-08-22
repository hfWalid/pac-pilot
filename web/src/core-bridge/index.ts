// The single point where the PWA talks to the Kotlin Multiplatform core.
//
// Everything the UI computes comes through here, so the JS target of the core is the only
// calculation path on the device — the same source the server re-runs to verify (ARCHITECTURE #3).
// Feature code must never import the Kotlin artifact directly: keeping it behind this module is
// what lets the import shape change without touching a single screen, and it is the only reason
// the screens can stay ignorant of Kotlin's generated declarations.
//
// Nothing here computes. If a calculation appears in this file, it belongs in `commonMain` instead,
// or the one-source-two-targets property quietly erodes.

import { fr } from 'pac-pilot-core';

const core = fr.pacpilot.core;
const facade = core.CoreFacade;

export type ConstructionPeriod =
  | 'BEFORE_1975'
  | 'FROM_1975_TO_1989'
  | 'FROM_1990_TO_2000'
  | 'FROM_2001_TO_2012'
  | 'AFTER_2012';

export type InsulationLevel = 'NONE' | 'PARTIAL' | 'GOOD';
export type VentilationType = 'NATURAL' | 'VMC_SIMPLE_FLUX' | 'VMC_DOUBLE_FLUX';
export type EmitterType =
  | 'RADIATOR_HIGH_TEMPERATURE'
  | 'RADIATOR_LOW_TEMPERATURE'
  | 'UNDERFLOOR_HEATING'
  | 'FAN_COIL';
export type ClimateZone = 'H1' | 'H2' | 'H3';
export type HeatPumpType = 'AIR_WATER' | 'AIR_AIR';
export type ReplacedSystem = 'OIL_BOILER' | 'GAS_BOILER' | 'ELECTRIC_HEATING' | 'OTHER';

/** Everything the engine needs, in the exact minor units the domain holds. */
export interface DimensioningInputs {
  /** Hundredths of a square metre. 120 m² is 12000 — never a float. */
  surfaceCentiM2: number;
  ceilingHeightCm: number;
  constructionPeriod: ConstructionPeriod;
  insulationLevel: InsulationLevel;
  ventilationType: VentilationType;
  emitterType: EmitterType;
  climateZone: ClimateZone;
  /** Tenths of a degree. −7,0 °C is −70. */
  baseTemperatureDeciC: number;
  targetIndoorTemperatureDeciC: number;
  electricalSupplyKva: number;
  /** YYYY-MM-DD. Selects the method version — there is no clock in the core. */
  effectiveDate: string;
}

export interface Assumption {
  statement: string;
  source: string;
  provisional: boolean;
}

/**
 * What the method answered — or its refusal to answer.
 *
 * A refusal is not an error. `MANUAL_STUDY_REQUIRED` means the dwelling fell outside the validated
 * envelope, and PRODUCT-VIEWS #9 wants an explicit banner rather than an error state.
 */
export type DimensioningResult =
  | {
      outcome: 'COMPUTED';
      heatLoadKw: string;
      heatLoadWatts: number;
      powerBandMinimumKw: string;
      powerBandMinimumWatts: number;
      powerBandMaximumKw: string;
      powerBandMaximumWatts: number;
      flowTemperatureC: string | null;
      confidence: 'INDICATIVE' | 'SUPPORTED';
      provisional: boolean;
      assumptions: Assumption[];
    }
  | {
      outcome: 'MANUAL_STUDY_REQUIRED';
      refusalReasons: string[];
      /** The domain's own wording, in French. Present it; do not invent a different meaning. */
      refusalStatements: string[];
    };

export interface AidLine {
  rule: string;
  label: string;
  amount: string;
  amountCents: number;
  source: string;
}

/**
 * The aids, or the reason there are none.
 *
 * `NO_PACK_PUBLISHED` is not zero. Zero is a claim about the household; a refusal is a statement
 * about the system (ADR-0017), and a homeowner told "0 €" would reasonably conclude they qualify
 * for nothing.
 */
export type AidsResult =
  | {
      outcome: 'RESOLVED';
      packVersion: string;
      lines: AidLine[];
      totalAids: string;
      vat: string;
      estimatedTotalIncludingVat: string;
      estimatedResteACharge: string;
      overGranted: boolean;
    }
  | { outcome: 'NO_PACK_PUBLISHED'; refusalDate: string };

/** Identifies the core build the PWA is linked against. */
export function identifyCore(): string {
  return facade.identify();
}

/**
 * Installs the provisional method — placeholder coefficients, every result `INDICATIVE`.
 *
 * Called once at start-up, by name, because there is nothing else to install until the ⚑ gate
 * (PAC-42) closes and because ADR-0021 gives the core no default. A PWA that skipped this would
 * compute nothing rather than compute wrongly.
 */
export function installProvisionalMethod(): void {
  facade.installProvisionalFormulaSet();
}

/** True while the installed method is unvalidated — what the degraded-mode banner reads. */
export function isMethodProvisional(): boolean {
  return facade.isMethodProvisional();
}

export function runDimensioning(inputs: DimensioningInputs): DimensioningResult {
  const result = facade.runDimensioning(
    inputs.surfaceCentiM2,
    inputs.ceilingHeightCm,
    inputs.constructionPeriod,
    inputs.insulationLevel,
    inputs.ventilationType,
    inputs.emitterType,
    inputs.climateZone,
    inputs.baseTemperatureDeciC,
    inputs.targetIndoorTemperatureDeciC,
    inputs.electricalSupplyKva,
    inputs.effectiveDate,
  );

  if (result.outcome === 'COMPUTED') {
    return {
      outcome: 'COMPUTED',
      heatLoadKw: result.heatLoadKw!,
      heatLoadWatts: result.heatLoadWatts,
      powerBandMinimumKw: result.powerBandMinimumKw!,
      powerBandMinimumWatts: result.powerBandMinimumWatts,
      powerBandMaximumKw: result.powerBandMaximumKw!,
      powerBandMaximumWatts: result.powerBandMaximumWatts,
      flowTemperatureC: result.flowTemperatureC ?? null,
      confidence: result.confidence as 'INDICATIVE' | 'SUPPORTED',
      provisional: result.provisional,
      assumptions: Array.from(result.assumptions).map((a) => ({
        statement: a.statement,
        source: a.source,
        provisional: a.provisional,
      })),
    };
  }

  return {
    outcome: 'MANUAL_STUDY_REQUIRED',
    refusalReasons: Array.from(result.refusalReasons),
    refusalStatements: Array.from(result.refusalStatements),
  };
}

export interface AidsInputs {
  incomeDecile: number;
  heatPumpType: HeatPumpType;
  climateZone: ClimateZone;
  replacedSystem: ReplacedSystem;
  workCostCents: number;
  effectiveDate: string;
}

export function resolveAids(inputs: AidsInputs): AidsResult {
  const result = facade.resolveAids(
    inputs.incomeDecile,
    inputs.heatPumpType,
    inputs.climateZone,
    inputs.replacedSystem,
    inputs.workCostCents,
    inputs.effectiveDate,
  );

  if (result.outcome === 'RESOLVED') {
    return {
      outcome: 'RESOLVED',
      packVersion: result.packVersion!,
      lines: Array.from(result.lines).map((l) => ({
        rule: l.rule,
        label: l.label,
        amount: l.amount,
        amountCents: l.amountCents,
        source: l.source,
      })),
      totalAids: result.totalAids!,
      vat: result.vat!,
      estimatedTotalIncludingVat: result.estimatedTotalIncludingVat!,
      estimatedResteACharge: result.estimatedResteACharge!,
      overGranted: result.overGranted,
    };
  }

  return { outcome: 'NO_PACK_PUBLISHED', refusalDate: result.refusalDate! };
}

/** Renders exact cents the way the domain does — one rounding rule across both targets. */
export function renderMoney(cents: number): string {
  return facade.renderMoney(cents);
}
