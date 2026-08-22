-- Dossier context (M4-03). The client and the site the work happens at.
--
-- Ownership: DELIVERY-PLAN §3 puts Client and Site server-side. Neither carries a calculation, so
-- neither belongs in the KMP core, and InputsSnapshot deliberately *copies* a site's
-- characteristics rather than pointing at a row that can change under a signed study.
--
-- Personal data: every column below is accounted for in ADR-0014 — lawful basis, retention and
-- erasure behaviour. `anonymised_at` exists from this first migration rather than a later one,
-- because erasure is a state the schema has to be able to express on day one.
--
-- Table names are prefixed with the owning context. BoundedContextRulesTest fails the build if an
-- entity in another context maps one of these.

-- The placeholder from V1 has served its purpose: real schema now exists.
DROP TABLE IF EXISTS schema_baseline;

CREATE TABLE dossier_client (
    -- Client-generated (CLAUDE.md §4.3). Aggregates are born offline in a cellar, so the database
    -- never mints an id: no sequence, no @GeneratedValue. An insert may legitimately arrive twice;
    -- M8 makes ingestion idempotent, and this key design is what allows it to.
    id              uuid        PRIMARY KEY,
    installer_id    uuid        NOT NULL,

    -- ADR-0014: identity, contract basis, anonymised on erasure.
    first_name      text        NOT NULL,
    last_name       text        NOT NULL,
    -- ADR-0014: contact, contract basis, hard-deleted on erasure — hence nullable from the start.
    email           text,
    phone           text,

    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    anonymised_at   timestamptz,

    CONSTRAINT dossier_client_named CHECK (length(trim(first_name)) > 0 AND length(trim(last_name)) > 0)
);

COMMENT ON COLUMN dossier_client.anonymised_at IS
    'Set when the data subject exercised erasure. Names are overwritten, email and phone are '
    'hard-deleted, and studies survive de-linked. See ADR-0014.';

CREATE TABLE dossier_site (
    id                  uuid        PRIMARY KEY,
    client_id           uuid        NOT NULL REFERENCES dossier_client (id),

    -- ADR-0014: location, contract basis, anonymised with the study that used it.
    address_line        text        NOT NULL,
    postcode            text        NOT NULL,
    commune             text        NOT NULL,
    -- Département drives the climate zone and the base temperature. Two characters covers 01–95;
    -- three covers 971–976 and 2A/2B, which is why this is text and not an integer.
    departement_code    text        NOT NULL,

    -- ADR-0014: opportunistic BAN geocode, legitimate interest, hard-deleted on erasure. Nullable
    -- and never blocking: geocoding happens when there is network, and a cellar has none.
    latitude            numeric(9, 6),
    longitude           numeric(9, 6),

    -- Dwelling characteristics, in the exact integer units the core's value objects hold, so the
    -- mapping is lossless in both directions. Stored as observed today; a study copies them.
    surface_centi_m2    integer     NOT NULL,
    ceiling_height_cm   integer     NOT NULL,
    construction_period text        NOT NULL,
    insulation_level    text        NOT NULL,
    ventilation_type    text        NOT NULL,
    emitter_type        text        NOT NULL,
    electrical_supply_kva integer   NOT NULL,

    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    anonymised_at       timestamptz,

    CONSTRAINT dossier_site_surface_positive CHECK (surface_centi_m2 > 0),
    CONSTRAINT dossier_site_ceiling_positive CHECK (ceiling_height_cm > 0),
    CONSTRAINT dossier_site_supply_positive  CHECK (electrical_supply_kva > 0),

    -- Text with a check constraint rather than a Postgres enum type, deliberately and throughout
    -- this schema: adding a member to a domain enum should be a migration that alters a constraint,
    -- not one that rewrites a type every dependent column and index refers to.
    CONSTRAINT dossier_site_construction_period CHECK (construction_period IN
        ('BEFORE_1975', 'FROM_1975_TO_1989', 'FROM_1990_TO_2000', 'FROM_2001_TO_2012', 'AFTER_2012')),
    CONSTRAINT dossier_site_insulation_level CHECK (insulation_level IN ('NONE', 'PARTIAL', 'GOOD')),
    CONSTRAINT dossier_site_ventilation_type CHECK (ventilation_type IN
        ('NATURAL', 'VMC_SIMPLE_FLUX', 'VMC_DOUBLE_FLUX')),
    CONSTRAINT dossier_site_emitter_type CHECK (emitter_type IN
        ('RADIATOR_HIGH_TEMPERATURE', 'RADIATOR_LOW_TEMPERATURE', 'UNDERFLOOR_HEATING', 'FAN_COIL'))
);

CREATE INDEX dossier_site_client_idx ON dossier_site (client_id);
CREATE INDEX dossier_client_installer_idx ON dossier_client (installer_id);
