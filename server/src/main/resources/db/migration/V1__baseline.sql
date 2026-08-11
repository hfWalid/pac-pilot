-- Baseline migration. Establishes the Flyway history table on a fresh database and nothing else.
--
-- No schema here by design: aggregates arrive at M4, once the domain model exists and the
-- ownership table in DELIVERY-PLAN §3 has been settled. Creating tables ahead of the model would
-- guess at a shape the domain has not yet defined.
--
-- Migration rules for everything that follows (CLAUDE.md §10):
--   * Forward-only. Never edit a migration that has been applied anywhere but a local machine —
--     Flyway records a checksum, and changing an applied file breaks every other environment.
--   * Reference data (climate zones, default U-values, the PAC catalog) is seeded by versioned
--     migrations, never by hand.
--   * One migration, one coherent change, reviewed like code.

-- Records that the baseline ran. Superseded by real schema at M4; safe to drop then.
CREATE TABLE IF NOT EXISTS schema_baseline (
    applied_at timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE schema_baseline IS
    'Placeholder from V1__baseline.sql (M0-05). No domain meaning. Remove when M4 lands real schema.';

INSERT INTO schema_baseline DEFAULT VALUES;
