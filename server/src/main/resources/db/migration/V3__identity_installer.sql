-- Identity context (ADR-0013). The artisan, as an account of record.
--
-- No credentials, no sessions, no signup: authentication is M10's, and this table asserts exactly
-- one thing — this account exists and work is attributed to it. What it does NOT assert is that a
-- given request came from that installer. Nothing before M10 may claim so.
--
-- It lands now rather than at M10 because `validated_by` is the legal shield (CLAUDE.md §4.5):
-- without this table, every validation act persisted between M4 and M10 would attribute a study to
-- an installer the system cannot name, and the audit chain would carry a four-epic hole.
--
-- Arrives after dossier_client (V2) rather than before it, so the foreign key that was always
-- implied by `installer_id` is added here rather than by editing an applied migration. Flyway is
-- forward-only, and that rule holds even when the only place a migration has run is a local
-- container.

CREATE TABLE identity_installer (
    -- Client-generated like every other aggregate, for the same reason: an installer's account can
    -- be created on the device before it has ever reached the server.
    id                  uuid        PRIMARY KEY,

    display_name        text        NOT NULL,
    -- The business identifier. 14 digits, and text rather than a number because it is an
    -- identifier, not a quantity: leading zeros are significant and no arithmetic is ever done on it.
    siret               text        NOT NULL,
    -- The RGE / QualiPAC qualification the artisan holds. Nullable because an account can exist
    -- before the reference has been recorded, and blocking account creation on it would put the
    -- system in the way of the work.
    qualification_ref   text,

    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,

    CONSTRAINT identity_installer_named   CHECK (length(trim(display_name)) > 0),
    CONSTRAINT identity_installer_siret   CHECK (siret ~ '^[0-9]{14}$'),
    CONSTRAINT identity_installer_siret_unique UNIQUE (siret)
);

COMMENT ON TABLE identity_installer IS
    'Account of record (ADR-0013). Attribution without authentication until M10.';

-- The reference dossier_client.installer_id always meant. Adding it now closes the gap where a
-- client could be attributed to an installer that does not exist.
ALTER TABLE dossier_client
    ADD CONSTRAINT dossier_client_installer_fk
    FOREIGN KEY (installer_id) REFERENCES identity_installer (id);
