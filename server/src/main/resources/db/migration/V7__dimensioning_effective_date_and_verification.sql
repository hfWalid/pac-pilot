-- Two things M4-07 needs, and the first of them is a hole rather than a feature.
--
-- 1. THE STUDY'S EFFECTIVE DATE.
--
-- RunDimensioning has always taken an effective date to select the formula set, but the Dimensioning
-- aggregate did not keep it — so nothing downstream could say which version of the method a stored
-- study was computed under. CLAUDE.md §4.2 requires the verifier to recompute from stored inputs AND
-- recorded versions, never from whatever is current; without this column it could only recompute
-- against today's method, reporting a divergence every time the method changed and passing every
-- time it had not. Quote has carried the equivalent field since M1-08 for the barème side.
--
-- Backfill: dimensioning_study is empty in every environment (no deployment exists — the epic that
-- creates one is M11), so a plain NOT NULL is honest here. If that ever stops being true, this
-- becomes add-nullable / backfill / set-not-null.

ALTER TABLE dimensioning_study
    ADD COLUMN effective_date date NOT NULL;

COMMENT ON COLUMN dimensioning_study.effective_date IS
    'The date whose formula set produced this result. Recompute against this, never against today.';

-- 2. THE VERIFICATION VERDICT.
--
-- The server recomputes and compares; divergence is persisted and flagged, NEVER corrected
-- (CLAUDE.md §4.2). There is deliberately no column that could hold a "corrected" value: the stored
-- result is what the installer signed, and a server that quietly rewrote it would be replacing
-- evidence.
--
-- One row per verification run, not one per study: a study verified again after a method correction
-- has two verdicts, and both are interesting. The history is the point.
CREATE TABLE dimensioning_verification (
    id              uuid        PRIMARY KEY,
    study_id        uuid        NOT NULL REFERENCES dimensioning_study (id) ON DELETE CASCADE,
    verified_at     timestamptz NOT NULL,
    -- MATCHED | DIVERGED | NOT_VERIFIABLE. The third is not a failure: it is the honest answer when
    -- no validated method is in force (ADR-0015) or no barème pack covers the date. Collapsing it
    -- into MATCHED would report an unverified result as verified, which is the one outcome that
    -- must never happen.
    outcome         text        NOT NULL,
    -- Human-readable, one line per differing field: "heatLoad: stored 19.032, recomputed 19.031".
    -- "Mismatch" sends someone to a debugger; a named field sends them to the formula set.
    differences     text,
    -- Why a verification could not be performed. Set exactly when outcome is NOT_VERIFIABLE.
    reason          text,

    CONSTRAINT dimensioning_verification_outcome_known
        CHECK (outcome IN ('MATCHED', 'DIVERGED', 'NOT_VERIFIABLE')),
    CONSTRAINT dimensioning_verification_diverged_names_fields
        CHECK ((outcome = 'DIVERGED') = (differences IS NOT NULL)),
    CONSTRAINT dimensioning_verification_unverifiable_says_why
        CHECK ((outcome = 'NOT_VERIFIABLE') = (reason IS NOT NULL))
);

CREATE INDEX dimensioning_verification_by_study ON dimensioning_verification (study_id, verified_at DESC);
