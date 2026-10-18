ALTER TABLE users ADD COLUMN contact_note varchar(200);

CREATE TABLE proposal (
    id            uuid         PRIMARY KEY,
    proposer_id   uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id  uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status        varchar(20)  NOT NULL,
    cancel_cause  varchar(30),
    message       varchar(500),
    reject_reason varchar(500),
    parent_id     uuid         REFERENCES proposal (id),
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    CONSTRAINT proposal_not_to_self CHECK (proposer_id <> recipient_id),
    CONSTRAINT proposal_status_valid CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED', 'COUNTERED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT proposal_cancel_cause_valid CHECK (cancel_cause IS NULL OR cancel_cause IN ('WITHDRAWN', 'COPY_UNAVAILABLE'))
);

CREATE TABLE proposal_item (
    proposal_id       uuid        NOT NULL REFERENCES proposal (id) ON DELETE CASCADE,
    copy_id           uuid        NOT NULL REFERENCES copy (id) ON DELETE CASCADE,
    side              varchar(10) NOT NULL,
    grade_at_proposal varchar(20) NOT NULL,
    PRIMARY KEY (proposal_id, copy_id),
    CONSTRAINT proposal_item_side_valid CHECK (side IN ('OFFERED', 'REQUESTED')),
    CONSTRAINT proposal_item_grade_valid CHECK (grade_at_proposal IN ('NEW', 'LIKE_NEW', 'GOOD', 'ACCEPTABLE', 'POOR'))
);

-- Inboxes: incoming and outgoing, filtered by status, newest first.
CREATE INDEX proposal_recipient_status_created_idx ON proposal (recipient_id, status, created_at DESC);
CREATE INDEX proposal_proposer_status_created_idx ON proposal (proposer_id, status, created_at DESC);
-- "Which open proposals contain this copy?" (accept, unlist, regrade, remove).
CREATE INDEX proposal_item_copy_idx ON proposal_item (copy_id);
-- The step 5 expiry job scans open proposals by expiry.
CREATE INDEX proposal_open_expiry_idx ON proposal (expires_at) WHERE status = 'PROPOSED';
