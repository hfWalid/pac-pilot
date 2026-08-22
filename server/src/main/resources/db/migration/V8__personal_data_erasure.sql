-- RGPD by design, in the schema itself (ADR-0014, PAC-60).
--
-- 1. THE INCOME DECILE.
--
-- ADR-0014 decided the fiscal income band IS stored, and only as a band: the server verifier
-- recomputes the aids from the stored inputs plus the recorded pack version and asserts equality
-- (CLAUDE.md §4.2), and a resolved aid with no recorded decile cannot be recomputed at all. That
-- would break the verification path at M4-07, the divergence check at M8, and the reproducibility
-- guarantee the whole pack architecture exists for.
--
-- The minimisation is that this is the ONLY thing stored about the household's finances. The product
-- never asks for, receives or stores a revenue figure, a tax notice, a household composition or a
-- numero fiscal. One integer in 1..10 is the least data that still computes the aid.
--
-- Nullable, because a devis can exist without aids having been resolved — which is every devis
-- today (ADR-0017). NULL means "no aids were resolved", never "decile unknown".
-- `integer`, not `smallint`: the entity maps it as a Java Integer, and Hibernate's schema
-- validation refuses the narrower column. Storage is irrelevant for one nullable value per devis,
-- and matching the mapping is worth more than two bytes.
ALTER TABLE quoting_quote
    ADD COLUMN income_decile integer;

ALTER TABLE quoting_quote
    ADD CONSTRAINT quoting_quote_income_decile_range
    CHECK (income_decile IS NULL OR income_decile BETWEEN 1 AND 10);

COMMENT ON COLUMN quoting_quote.income_decile IS
    'Fiscal income band 1-10 (ADR-0014). Sensitive. Retained de-linked on erasure, never rendered.';

-- 2. ERASURE MARKERS WHERE ERASURE MEANS ANONYMISATION.
--
-- dossier_client and dossier_site already carry anonymised_at from their first migration. Nothing is
-- added for dimensioning_study or quoting_quote on purpose, and that is not an oversight: ADR-0014's
-- table gives both "Retain, de-linked", which means they are never anonymised themselves. The
-- identity is severed at the client and the site, and everything downstream becomes unreachable from
-- a named person without being touched.
--
-- (ADR-0014 also contains a blanket sentence saying every table holding personal data carries
-- anonymised_at. Its own per-column table is narrower and is what is implemented; the blanket
-- sentence over-states. Recorded here rather than silently diverging.)

-- The erasure job at M11 needs to find clients due for anonymisation without scanning.
CREATE INDEX dossier_client_pending_erasure
    ON dossier_client (anonymised_at)
    WHERE anonymised_at IS NULL;
