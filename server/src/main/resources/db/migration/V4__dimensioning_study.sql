-- The Dimensioning context: a heat-loss study, its frozen inputs, and who signed it.
--
-- CROSS-CONTEXT FOREIGN KEYS: this migration references dossier_site, and V3 already referenced
-- identity_installer from dossier_client. That is a deliberate, repeated choice, recorded in
-- ADR-0016 rather than decided anew at each migration. One database, one deployable (CLAUDE.md
-- §4.7): referential integrity is worth more here than keeping extraction free, and the cost —
-- extraction becomes a data migration rather than a code change — is named in the ADR.
--
-- The validation act is stored as a NULLABLE PAIR, which is the one place the domain and the schema
-- genuinely disagree in shape. The domain has two types, ComputedDimensioning and
-- ValidatedDimensioning, and no way to hold half a signature. A table has one row shape. The check
-- constraint below is what stops the schema from expressing a state the domain cannot: a study
-- signed by nobody at a known instant, or by somebody at no instant, is corrupt rather than
-- unusual, and the mapper would have no case to map it to.

CREATE TABLE dimensioning_study (
    id                          uuid        PRIMARY KEY,
    site_id                     uuid        NOT NULL REFERENCES dossier_site (id),

    -- ── InputsSnapshot: the reproducibility anchor (CLAUDE.md §9, §10) ──────────────────────
    -- Every quantity is stored as its exact minor unit, as an integer, never as a float. The core's
    -- value types are fixed-point for exactly this reason: a study recomputed in 2029 has to land on
    -- the same watt, and a binary fraction that cannot represent 0.1 turns that into a divergence
    -- flag in front of a homeowner.
    surface_centi_m2            integer     NOT NULL,
    ceiling_height_cm           integer     NOT NULL,
    construction_period         text        NOT NULL,
    insulation_level            text        NOT NULL,
    ventilation_type            text        NOT NULL,
    emitter_type                text        NOT NULL,
    climate_zone                text        NOT NULL,
    -- Both the zone and the resolved base temperature, per InputsSnapshot's own reasoning: the zone
    -- is what an installer recognises, the resolved value is what the formula consumed. Storing only
    -- the zone would make a study irreproducible the day a département's tabulated value is
    -- corrected.
    base_temperature_deci_c     integer     NOT NULL,
    target_indoor_temp_deci_c   integer     NOT NULL,
    available_electrical_kva    integer     NOT NULL,

    -- ── HeatLoadResult ─────────────────────────────────────────────────────────────────────
    heat_load_w                 integer     NOT NULL,
    power_band_minimum_w        integer     NOT NULL,
    power_band_maximum_w        integer     NOT NULL,
    -- Nullable because the method may decline to advise a loi d'eau without that blocking the
    -- study. NULL here means "the method declined", which the assumptions log records the reason
    -- for — it does not mean "forgotten".
    flow_temperature_deci_c     integer,

    -- ── ValidationAct: the legal shield (CLAUDE.md §4.5) ────────────────────────────────────
    -- Distinct from any computation timestamp on purpose. When a study was calculated and when a
    -- professional took responsibility for it are different facts, and an auditor asks about the
    -- second.
    validated_by                uuid        REFERENCES identity_installer (id),
    validated_at                timestamptz,

    created_at                  timestamptz NOT NULL,

    CONSTRAINT dimensioning_study_validation_is_whole
        CHECK ((validated_by IS NULL) = (validated_at IS NULL)),
    CONSTRAINT dimensioning_study_temperature_gap
        CHECK (base_temperature_deci_c < target_indoor_temp_deci_c),
    CONSTRAINT dimensioning_study_band_runs_upward
        CHECK (power_band_minimum_w <= power_band_maximum_w)
);

CREATE INDEX dimensioning_study_by_site ON dimensioning_study (site_id);

COMMENT ON TABLE dimensioning_study IS
    'A heat-loss study. Signed when validated_by/validated_at are set, together or not at all.';

-- The assumptions log, as rows rather than as a serialised blob.
--
-- PAC-54 asked for this to be decided deliberately, so: a blob is smaller and simpler, and rows are
-- chosen anyway because the first question that will be asked of this data is already known. When
-- the M2 gate (PAC-42) lands a validated method, someone has to find every study computed under the
-- provisional one — "which studies rest on a SOURCE_TBD coefficient" is a WHERE clause against this
-- table and a full deserialisation sweep against a blob. M6 asks the same question of a corrected
-- barème coefficient.
--
-- Ordinal is stored because the log is ordered: it is the order the method made its assumptions in,
-- and it is what an auditor reads down.
CREATE TABLE dimensioning_assumption (
    study_id    uuid        NOT NULL REFERENCES dimensioning_study (id) ON DELETE CASCADE,
    ordinal     integer     NOT NULL,
    statement   text        NOT NULL,
    -- A citation, or the literal SOURCE_TBD with a reason. Never blank — an unsourced number that
    -- looks authoritative is the failure mode this project cannot afford (CLAUDE.md §12).
    source      text        NOT NULL,

    PRIMARY KEY (study_id, ordinal),
    CONSTRAINT dimensioning_assumption_states_something CHECK (length(trim(statement)) > 0),
    CONSTRAINT dimensioning_assumption_cites_something  CHECK (length(trim(source)) > 0)
);

-- The query PAC-42 will need on the day it closes.
CREATE INDEX dimensioning_assumption_by_source ON dimensioning_assumption (source);
