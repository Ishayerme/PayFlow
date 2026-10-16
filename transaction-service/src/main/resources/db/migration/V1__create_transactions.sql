-- The ledger: one row per transfer attempt, successful or not.
CREATE TABLE transactions (
    id              BIGSERIAL PRIMARY KEY,
    reference       UUID          NOT NULL UNIQUE,
    from_account_id BIGINT        NOT NULL,
    to_account_id   BIGINT        NOT NULL,
    amount          NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status          VARCHAR(20)   NOT NULL,     -- PENDING, COMPLETED or FAILED
    failure_reason  VARCHAR(255),
    initiated_by    BIGINT        NOT NULL,     -- user id from the JWT
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- A statement asks: "latest transactions where this account sent OR received money".
-- One composite index per side lets PostgreSQL read each side already sorted
-- by date and stop after LIMIT rows, instead of scanning the whole table.
CREATE INDEX idx_transactions_from_created ON transactions (from_account_id, created_at DESC);
CREATE INDEX idx_transactions_to_created   ON transactions (to_account_id, created_at DESC);
