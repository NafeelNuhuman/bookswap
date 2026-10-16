CREATE TABLE book (
    isbn13     varchar(13)   PRIMARY KEY,
    title      varchar(500)  NOT NULL,
    author     varchar(500)  NOT NULL,
    cover_url  varchar(1000),
    created_at timestamptz   NOT NULL,
    CONSTRAINT book_isbn13_digits CHECK (isbn13 ~ '^[0-9]{13}$'),
    CONSTRAINT book_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT book_author_not_blank CHECK (btrim(author) <> ''),
    CONSTRAINT book_cover_https CHECK (cover_url IS NULL OR cover_url LIKE 'https://%')
);

CREATE TABLE copy (
    id         uuid        PRIMARY KEY,
    owner_id   uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    isbn13     varchar(13) NOT NULL REFERENCES book (isbn13),
    grade      varchar(20) NOT NULL,
    status     varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL,
    CONSTRAINT copy_grade_valid CHECK (grade IN ('NEW', 'LIKE_NEW', 'GOOD', 'ACCEPTABLE', 'POOR')),
    CONSTRAINT copy_status_valid CHECK (status IN ('AVAILABLE', 'UNLISTED', 'RESERVED'))
);

CREATE INDEX copy_owner_status_created_idx ON copy (owner_id, status, created_at DESC);
CREATE INDEX copy_isbn_idx ON copy (isbn13);
