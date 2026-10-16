CREATE TABLE users (
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    display_name  varchar(60),
    city          varchar(100),
    postal_code   varchar(20),
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    CONSTRAINT users_profile_all_or_none CHECK (
        (display_name IS NULL AND city IS NULL AND postal_code IS NULL)
        OR (coalesce(btrim(display_name), '') <> ''
            AND coalesce(btrim(city), '') <> ''
            AND coalesce(btrim(postal_code), '') <> '')
    )
);

CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE refresh_token (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    revoked_at timestamptz
);

CREATE INDEX refresh_token_user_idx ON refresh_token (user_id);
