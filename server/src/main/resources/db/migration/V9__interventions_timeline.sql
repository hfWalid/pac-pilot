-- The Interventions context: the artisan's own timeline of visits (CLAUDE.md §14).
--
-- Lands at M4 rather than M7.5 by ADR-0012, so "all aggregates are migrated" is true on the day M4
-- is signed off. M7.5 keeps everything that is genuinely PWA work — the timeline screen, offline
-- CRUD, local filters, geocoding, maintenance recurrence — and adds no schema change.
--
-- This is NOT a general agenda and must never present itself as one. It holds only what the app
-- owns: visits attached to a Site. That is what stops it going stale, because nothing external
-- writes to it.

CREATE TABLE interventions_intervention (
    id                  uuid        PRIMARY KEY,
    site_id             uuid        NOT NULL REFERENCES dossier_site (id),

    type                text        NOT NULL,

    -- TEXT WITH A CHECK CONSTRAINT, NEVER A POSTGRES ENUM TYPE. The V1.5 booking path adds
    -- REQUESTED / CONFIRMED / DECLINED in front of this set, and it has to be an addition rather
    -- than a migration of meaning (§14, ADR-0012). Adding a member to a check constraint is a
    -- data-definition change on one line; adding one to an enum type is a schema migration that
    -- rewrites every dependent object.
    status              text        NOT NULL,

    scheduled_at        timestamptz NOT NULL,
    duration_minutes    integer     NOT NULL,

    -- DENORMALISED ON PURPOSE (§14). Where a visit was recorded as having happened must not change
    -- because the Site was later corrected — the same reasoning that makes InputsSnapshot copy what
    -- it needs. This is personal data and PAC-60's erasure sweep reaches it; the sweep discovers
    -- columns from information_schema precisely so a denormalised copy like this one cannot be
    -- forgotten.
    address_snapshot    text        NOT NULL,

    -- Geocoded via BAN when the network allows. Nullable and never blocking: an installer standing
    -- in a cellar with no signal must still be able to create and complete a visit.
    latitude            numeric(9, 7),
    longitude           numeric(10, 7),

    outcome_notes       text,

    -- A completed PRE_VISIT links to what it produced — the audit chain of PRODUCT-VIEWS #8 gains
    -- an entry point. Optional because most visits produce neither, and one-way: the study and the
    -- devis have no idea a visit exists.
    dimensioning_id     uuid        REFERENCES dimensioning_study (id),
    quote_id            uuid        REFERENCES quoting_quote (id),

    -- Captured on every cancel and no-show. The reason is the useful half: a timeline of
    -- cancellations without reasons tells an artisan nothing they did not already know.
    cancellation_reason text,

    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,

    CONSTRAINT interventions_type_known
        CHECK (type IN ('PRE_VISIT', 'INSTALL', 'MAINTENANCE', 'SAV')),
    CONSTRAINT interventions_status_known
        CHECK (status IN ('PLANNED', 'DONE', 'CANCELLED', 'RESCHEDULED', 'NO_SHOW')),
    CONSTRAINT interventions_duration_positive
        CHECK (duration_minutes > 0),
    CONSTRAINT interventions_address_recorded
        CHECK (length(trim(address_snapshot)) > 0),
    -- A cancellation without a reason is the state §14 says must not exist.
    CONSTRAINT interventions_cancellation_explained
        CHECK (status NOT IN ('CANCELLED', 'NO_SHOW') OR cancellation_reason IS NOT NULL)
);

-- The timeline home screen reads "aujourd'hui / cette semaine / a faire" off this.
CREATE INDEX interventions_by_schedule ON interventions_intervention (scheduled_at);
CREATE INDEX interventions_by_site ON interventions_intervention (site_id, scheduled_at DESC);

COMMENT ON TABLE interventions_intervention IS
    'The artisan''s own visits (CLAUDE.md §14). Not an agenda: it holds only what this app owns.';
