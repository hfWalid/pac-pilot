-- The Quoting context: the devis, its lines, and the aids that were resolved for it.
--
-- This is the artefact that leaves the system and ends up in a client's file, an insurer's file, or
-- an auditor's hands. Everything here exists to make it reproducible years later, and every column
-- resists the same shortcut: store the id, store the total, join to the live catalogue.
--
-- NO STORED TOTALS. Not the subtotal, not the VAT, not the TTC, not the reste-à-charge. The domain
-- derives all four from the lines, and a stored copy is a second source of truth that drifts from
-- them the first time a line is corrected. It is also the number the homeowner reads off the screen,
-- so a drift is not a reporting inconvenience — it is a devis that contradicts itself.

CREATE TABLE quoting_quote (
    id                      uuid        PRIMARY KEY,

    -- The study this devis was built on. A Quote holds a ValidatedDimensioning, not an id, and
    -- ARCHITECTURE #7 allows only Validated → Quoted — so a row pointing at an unsigned study is
    -- corrupt. The database cannot express "signed" on its own; the mapper refuses it loudly on
    -- load, and that refusal is tested.
    dimensioning_id         uuid        NOT NULL REFERENCES dimensioning_study (id),

    -- ── ProductSnapshot, stored INLINE and deliberately not a foreign key to catalog ────────
    -- A machine discontinued next year, a price revised, a specification corrected — any of those
    -- would silently rewrite a devis issued last year if this were a reference. The document in the
    -- client's file would stop matching the system's account of it. Only the attributes that must
    -- survive are copied: what was sold, the figure it was selected on, and what it cost that day.
    product_id              text        NOT NULL,
    product_model           text        NOT NULL,
    product_power_at_m7_w   integer     NOT NULL,
    product_price_cents     bigint      NOT NULL,

    -- The barème version that priced the aid lines. This, not the amounts, is what makes the devis
    -- reproducible: packs are immutable and never deleted, so the reference stays resolvable even
    -- after the barème has been superseded three times (CLAUDE.md §7).
    aid_pack_version        text        NOT NULL,

    -- The devis date, which selected both the rule pack and the formula set. A date, not a
    -- timestamp: a barème applies to a day, and a time zone here would put a devis written at
    -- 23:30 in Lyon on the wrong side of a handover.
    effective_date          date        NOT NULL,

    status                  text        NOT NULL,
    created_at              timestamptz NOT NULL,

    CONSTRAINT quoting_quote_status_known
        CHECK (status IN ('DRAFT', 'QUOTED', 'AIDS_RESOLVED', 'SENT', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT quoting_quote_product_named CHECK (length(trim(product_model)) > 0),
    CONSTRAINT quoting_quote_pack_named    CHECK (length(trim(aid_pack_version)) > 0)
);

CREATE INDEX quoting_quote_by_dimensioning ON quoting_quote (dimensioning_id);

COMMENT ON TABLE quoting_quote IS
    'The devis. Totals are derived from quoting_line_item, never stored (PAC-55).';

-- One priced line. The VAT rate sits here rather than on the quote because TVA at 5,5 % applies
-- conditionally (CLAUDE.md §6b): the machine and its installation can qualify while an unrelated
-- item on the same devis does not.
CREATE TABLE quoting_line_item (
    quote_id            uuid        NOT NULL REFERENCES quoting_quote (id) ON DELETE CASCADE,
    ordinal             integer     NOT NULL,
    label               text        NOT NULL,
    unit_price_cents    bigint      NOT NULL,
    quantity            integer     NOT NULL,
    -- Basis points, so the rate is exact. 5,5 % is 550; a float here is how a client-side
    -- reste-à-charge ends up a cent from the server's.
    vat_basis_points    integer     NOT NULL,

    PRIMARY KEY (quote_id, ordinal),
    -- Mirrors LineItem's own invariants, so a bad row cannot exist even if written outside the
    -- adapter.
    CONSTRAINT quoting_line_item_described     CHECK (length(trim(label)) > 0),
    CONSTRAINT quoting_line_item_quantity      CHECK (quantity > 0),
    CONSTRAINT quoting_line_item_vat_not_negative CHECK (vat_basis_points >= 0)
);

-- The aids as they were resolved for this devis, itemised.
--
-- Itemisation is not presentation: a homeowner comparing two devis needs MaPrimeRénov' and the CEE
-- separately, and an artisan defending a figure needs to name the fiche. The rule id is the audit
-- trail back to the barème — "BAR-TH-171" is an answer, "aides: 5 400 €" is not.
CREATE TABLE quoting_aid_line (
    quote_id        uuid        NOT NULL REFERENCES quoting_quote (id) ON DELETE CASCADE,
    ordinal         integer     NOT NULL,
    rule_id         text        NOT NULL,
    label           text        NOT NULL,
    amount_cents    bigint      NOT NULL,
    source          text        NOT NULL,

    PRIMARY KEY (quote_id, ordinal),
    CONSTRAINT quoting_aid_line_named  CHECK (length(trim(label)) > 0),
    CONSTRAINT quoting_aid_line_cited  CHECK (length(trim(source)) > 0),
    -- An aid does not take money away; a deduction is modelled as a quote line (M1-07).
    CONSTRAINT quoting_aid_line_not_negative CHECK (amount_cents >= 0)
);
