-- Manual title/author belong to the copy, never to the shared book row, so a copy can exist for an ISBN
-- that has no book row (yet). The display rule (catalog book wins over typed details) lives in code.
ALTER TABLE copy DROP CONSTRAINT copy_isbn13_fkey;

ALTER TABLE copy
    ADD COLUMN title  varchar(500),
    ADD COLUMN author varchar(500),
    ADD CONSTRAINT copy_isbn13_digits CHECK (isbn13 ~ '^[0-9]{13}$'),
    ADD CONSTRAINT copy_manual_details_both_or_none CHECK ((title IS NULL) = (author IS NULL)),
    ADD CONSTRAINT copy_manual_details_not_blank CHECK (
        title IS NULL OR (btrim(title) <> '' AND btrim(author) <> ''));
