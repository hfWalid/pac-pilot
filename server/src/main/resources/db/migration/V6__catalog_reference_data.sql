-- The Catalog context: reference data, seeded by migration and reproducible from a clean database.
--
-- Two tables with opposite seeding stories, and the difference is the whole point of this migration.
--
--   catalog_departement_climate  — schema only. NOT ONE ROW IS SEEDED.
--   catalog_product              — a handful of unmistakably synthetic rows, so the flow can run.
--
-- WHY THE CLIMATE TABLE IS EMPTY. The outdoor design temperature per département is a tabulated
-- regulatory value. This repository does not have a verified source for it, and PAC-56 says
-- plainly: seed the schema and leave the rows to a follow-up rather than filling them plausibly.
-- The same rule that governs the M2 ⚑ gate governs this table — an unsourced number that looks
-- authoritative is the failure mode this project cannot afford (CLAUDE.md §12), and this one would
-- feed straight into every heat load the product computes.
--
-- An empty table is a visible, blocking absence: a study for a département with no row cannot be
-- resolved at all, and the boundary refuses rather than guessing. A table filled with plausible
-- numbers is an invisible one.

CREATE TABLE catalog_departement_climate (
    -- Text, not a number: 2A and 2B for Corsica, three digits overseas. Parsing these as integers is
    -- the classic bug, and Departement in :core already refuses to.
    departement_code        text        PRIMARY KEY,
    zone                    text        NOT NULL,
    base_temperature_deci_c integer     NOT NULL,
    -- A citation, or the literal SOURCE_TBD with a reason. NOT NULL and non-blank, mirroring
    -- DepartementClimate's own invariant — the same discipline Sourced enforces in the core.
    source                  text        NOT NULL,

    CONSTRAINT catalog_departement_climate_zone_known
        CHECK (zone IN ('H1', 'H2', 'H3')),
    CONSTRAINT catalog_departement_climate_code_shape
        CHECK (departement_code = upper(trim(departement_code)) AND length(departement_code) > 0),
    CONSTRAINT catalog_departement_climate_cited
        CHECK (length(trim(source)) > 0)
);

COMMENT ON TABLE catalog_departement_climate IS
    'Departement -> zone + outdoor design temperature. Deliberately unseeded (PAC-56): no verified source yet.';

-- The PAC catalogue.
--
-- WHY THESE ROWS ARE SAFE TO SEED AND THE CLIMATE ROWS ARE NOT. A wrong base temperature silently
-- changes every heat load; a wrong catalogue entry names a machine that does not exist, which is
-- caught the first time anyone reads it. The rows below are also deliberately unmistakable — the
-- brand is literally "ECHANTILLON", every model says so, and each carries SOURCE_TBD. They exist so
-- the pre-visit flow can run end to end at M4-09, not because a catalogue has been sourced.
--
-- A real catalogue is a sourcing decision with licensing and freshness questions of its own
-- (PRODUCT-VIEWS #11), not a migration.
CREATE TABLE catalog_product (
    id                      text        PRIMARY KEY,
    brand                   text        NOT NULL,
    model                   text        NOT NULL,
    -- The selection criterion (CLAUDE.md §3). Watts, exact, never a float.
    power_at_minus_seven_w  integer     NOT NULL,
    -- SCOP varies by zone, so one column each. Milli-units: 3.850 is 3850.
    scop_h1_milli           integer     NOT NULL,
    scop_h2_milli           integer     NOT NULL,
    scop_h3_milli           integer     NOT NULL,
    -- Sound power, deci-decibels: 54.0 dB is 540. Neighbours and planning rules care about this.
    sound_power_deci_db     integer     NOT NULL,
    -- Which emitters the machine can drive, as the core's EmitterType names.
    compatible_emitters     text[]      NOT NULL,
    source                  text        NOT NULL,

    CONSTRAINT catalog_product_named   CHECK (length(trim(model)) > 0),
    CONSTRAINT catalog_product_cited   CHECK (length(trim(source)) > 0),
    CONSTRAINT catalog_product_emitters_present CHECK (cardinality(compatible_emitters) > 0)
);

COMMENT ON TABLE catalog_product IS
    'PAC catalogue. Seeded rows are synthetic placeholders (PAC-56); a real catalogue is a sourcing decision.';

-- Idempotent by construction: a second run of this migration never happens (Flyway records it), and
-- ON CONFLICT DO NOTHING means re-running the statement by hand is harmless too.
INSERT INTO catalog_product (
    id, brand, model, power_at_minus_seven_w,
    scop_h1_milli, scop_h2_milli, scop_h3_milli,
    sound_power_deci_db, compatible_emitters, source
) VALUES
    ('echantillon-06', 'ECHANTILLON', 'ECHANTILLON 6 kW (donnee non sourcee)',  6000, 3000, 3000, 3000, 500,
     ARRAY['RADIATOR_LOW_TEMPERATURE', 'UNDERFLOOR_HEATING'],
     'SOURCE_TBD (synthetic placeholder; a real catalogue is a sourcing decision, PAC-56)'),
    ('echantillon-08', 'ECHANTILLON', 'ECHANTILLON 8 kW (donnee non sourcee)',  8000, 3000, 3000, 3000, 500,
     ARRAY['RADIATOR_LOW_TEMPERATURE', 'UNDERFLOOR_HEATING', 'FAN_COIL'],
     'SOURCE_TBD (synthetic placeholder; a real catalogue is a sourcing decision, PAC-56)'),
    ('echantillon-12', 'ECHANTILLON', 'ECHANTILLON 12 kW (donnee non sourcee)', 12000, 3000, 3000, 3000, 500,
     ARRAY['RADIATOR_HIGH_TEMPERATURE', 'RADIATOR_LOW_TEMPERATURE', 'UNDERFLOOR_HEATING'],
     'SOURCE_TBD (synthetic placeholder; a real catalogue is a sourcing decision, PAC-56)'),
    ('echantillon-16', 'ECHANTILLON', 'ECHANTILLON 16 kW (donnee non sourcee)', 16000, 3000, 3000, 3000, 500,
     ARRAY['RADIATOR_HIGH_TEMPERATURE', 'RADIATOR_LOW_TEMPERATURE'],
     'SOURCE_TBD (synthetic placeholder; a real catalogue is a sourcing decision, PAC-56)')
ON CONFLICT (id) DO NOTHING;
