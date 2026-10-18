-- Step 5: confirming, completing and cancelling accepted exchanges, expiry housekeeping, history, account deletion.

-- Proposal: lifecycle columns and the two new states.
ALTER TABLE proposal
    ADD COLUMN accepted_at            timestamptz,
    ADD COLUMN confirm_by             timestamptz,
    ADD COLUMN proposer_confirmed_at  timestamptz,
    ADD COLUMN recipient_confirmed_at timestamptz,
    ADD COLUMN completed_at           timestamptz,
    ADD COLUMN pickup_note            varchar(500);

ALTER TABLE proposal DROP CONSTRAINT proposal_status_valid;
ALTER TABLE proposal ADD CONSTRAINT proposal_status_valid CHECK (
    status IN ('PROPOSED', 'ACCEPTED', 'REJECTED', 'COUNTERED', 'CANCELLED', 'EXPIRED', 'COMPLETED'));
ALTER TABLE proposal DROP CONSTRAINT proposal_cancel_cause_valid;
ALTER TABLE proposal ADD CONSTRAINT proposal_cancel_cause_valid CHECK (
    cancel_cause IS NULL OR cancel_cause IN ('WITHDRAWN', 'COPY_UNAVAILABLE', 'NO_SHOW'));

-- Accepted proposals that existed before this migration get the same 14-day window, counted from their last change.
UPDATE proposal SET accepted_at = updated_at, confirm_by = updated_at + interval '14 days' WHERE status = 'ACCEPTED';

CREATE INDEX proposal_parent_idx ON proposal (parent_id);
-- The housekeeping job scans accepted proposals by their confirm deadline.
CREATE INDEX proposal_accepted_deadline_idx ON proposal (confirm_by) WHERE status = 'ACCEPTED';
-- History: a user's completed exchanges, newest first.
CREATE INDEX proposal_completed_recipient_idx ON proposal (recipient_id, completed_at DESC) WHERE status = 'COMPLETED';
CREATE INDEX proposal_completed_proposer_idx ON proposal (proposer_id, completed_at DESC) WHERE status = 'COMPLETED';

-- Proposal items keep their own copy of the book's details, so history survives a removed copy.
ALTER TABLE proposal_item
    ADD COLUMN id             uuid,
    ADD COLUMN isbn13         varchar(13),
    ADD COLUMN title          varchar(500),
    ADD COLUMN author         varchar(500),
    ADD COLUMN cover_url      varchar(1000),
    ADD COLUMN details_source varchar(10),
    ADD COLUMN position       integer;

UPDATE proposal_item pi
SET id = gen_random_uuid(),
    isbn13 = c.isbn13,
    title = coalesce(b.title, c.title),
    author = coalesce(b.author, c.author),
    cover_url = b.cover_url,
    details_source = CASE WHEN b.isbn13 IS NULL THEN 'MANUAL' ELSE 'CATALOG' END,
    position = ranked.position
FROM copy c
LEFT JOIN book b ON b.isbn13 = c.isbn13,
     (SELECT i.proposal_id, i.copy_id,
             row_number() OVER (PARTITION BY i.proposal_id, i.side ORDER BY cc.created_at, cc.id) - 1 AS position
      FROM proposal_item i JOIN copy cc ON cc.id = i.copy_id) ranked
WHERE c.id = pi.copy_id AND ranked.proposal_id = pi.proposal_id AND ranked.copy_id = pi.copy_id;

ALTER TABLE proposal_item
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN isbn13 SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN author SET NOT NULL,
    ALTER COLUMN details_source SET NOT NULL,
    ALTER COLUMN position SET NOT NULL,
    ADD CONSTRAINT proposal_item_details_source_valid CHECK (details_source IN ('CATALOG', 'MANUAL'));

ALTER TABLE proposal_item DROP CONSTRAINT proposal_item_pkey;
ALTER TABLE proposal_item ADD PRIMARY KEY (id);
ALTER TABLE proposal_item ADD CONSTRAINT proposal_item_proposal_copy_uq UNIQUE (proposal_id, copy_id);
ALTER TABLE proposal_item ALTER COLUMN copy_id DROP NOT NULL;
ALTER TABLE proposal_item DROP CONSTRAINT proposal_item_copy_id_fkey;
ALTER TABLE proposal_item ADD CONSTRAINT proposal_item_copy_id_fkey
    FOREIGN KEY (copy_id) REFERENCES copy (id) ON DELETE SET NULL;

-- Accounts are anonymized, not deleted: other people's history keeps pointing at the row.
ALTER TABLE users ADD COLUMN deleted_at timestamptz;
ALTER TABLE users DROP CONSTRAINT users_profile_all_or_none;
ALTER TABLE users ADD CONSTRAINT users_profile_all_or_none CHECK (
    deleted_at IS NOT NULL
    OR (display_name IS NULL AND city IS NULL AND postal_code IS NULL)
    OR (coalesce(btrim(display_name), '') <> ''
        AND coalesce(btrim(city), '') <> ''
        AND coalesce(btrim(postal_code), '') <> ''));
