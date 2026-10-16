-- Flyway runs this once on an empty database and records that it ran.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,          -- CUSTOMER or ADMIN
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT        NOT NULL REFERENCES users (id),
    account_number VARCHAR(20)   NOT NULL UNIQUE,
    balance        NUMERIC(19,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL,        -- ACTIVE or FROZEN
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Last line of defence: even if application code had a bug,
    -- the database itself refuses to store a negative balance.
    CONSTRAINT balance_not_negative CHECK (balance >= 0)
);

-- "Show me my accounts" filters by user_id on every dashboard load.
CREATE INDEX idx_accounts_user_id ON accounts (user_id);
