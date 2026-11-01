-- ---------------------------------------------------------------------------
-- V2: double-entry ledger (see ADR-005)
--
-- Every movement of money is a journal entry made of >= 2 postings whose
-- signed amounts sum to exactly zero. Wallet balances are a projection of
-- this ledger, never the source of truth.
-- ---------------------------------------------------------------------------

CREATE TABLE ledger_account (
    id           UUID        PRIMARY KEY,
    account_type VARCHAR(24) NOT NULL,
    wallet_id    UUID,
    currency     VARCHAR(3)  NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_ledger_account_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id),
    CONSTRAINT ck_ledger_account_type
        CHECK (account_type IN ('WALLET_LIABILITY', 'CASH_IN_CLEARING', 'CASH_OUT_CLEARING')),
    -- A wallet account must point at a wallet; a system account must not.
    CONSTRAINT ck_ledger_account_wallet_link
        CHECK ((account_type = 'WALLET_LIABILITY' AND wallet_id IS NOT NULL)
            OR (account_type <> 'WALLET_LIABILITY' AND wallet_id IS NULL))
);

CREATE UNIQUE INDEX uq_ledger_account_wallet
    ON ledger_account (wallet_id) WHERE wallet_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ledger_account_system
    ON ledger_account (account_type, currency) WHERE wallet_id IS NULL;

CREATE TABLE journal_entry (
    id               UUID        PRIMARY KEY,
    entry_type       VARCHAR(16) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    currency         VARCHAR(3)  NOT NULL,
    amount_minor     BIGINT      NOT NULL,
    initiated_by     UUID        NOT NULL,
    reference        VARCHAR(140),
    failure_reason   VARCHAR(200),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_journal_entry_user FOREIGN KEY (initiated_by) REFERENCES app_user (id),
    CONSTRAINT ck_journal_entry_type
        CHECK (entry_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_journal_entry_status
        CHECK (status IN ('POSTED', 'FAILED')),
    -- Amounts are strictly positive; direction lives on the posting, not here.
    CONSTRAINT ck_journal_entry_amount CHECK (amount_minor > 0)
);

CREATE INDEX ix_journal_entry_initiator ON journal_entry (initiated_by, created_at DESC);

CREATE TABLE ledger_posting (
    id               UUID        PRIMARY KEY,
    journal_entry_id UUID        NOT NULL,
    account_id       UUID        NOT NULL,
    direction        VARCHAR(6)  NOT NULL,
    amount_minor     BIGINT      NOT NULL,
    -- Signed amount, maintained by the application: +amount for CREDIT,
    -- -amount for DEBIT. Lets the zero-sum invariant be checked with SUM().
    signed_minor     BIGINT      NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_posting_entry   FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT fk_posting_account FOREIGN KEY (account_id) REFERENCES ledger_account (id),
    CONSTRAINT ck_posting_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_posting_amount    CHECK (amount_minor > 0),
    CONSTRAINT ck_posting_sign
        CHECK ((direction = 'CREDIT' AND signed_minor =  amount_minor)
            OR (direction = 'DEBIT'  AND signed_minor = -amount_minor))
);

CREATE INDEX ix_posting_entry   ON ledger_posting (journal_entry_id);
CREATE INDEX ix_posting_account ON ledger_posting (account_id, created_at DESC);

-- Reconciliation view: any row returned means the ledger is unbalanced,
-- which must never happen. Used by the reconciliation endpoint and tests.
CREATE VIEW unbalanced_journal_entry AS
SELECT journal_entry_id, SUM(signed_minor) AS imbalance_minor
FROM ledger_posting
GROUP BY journal_entry_id
HAVING SUM(signed_minor) <> 0;

-- Reconciliation view: compares each wallet's cached balance against the
-- sum of its ledger postings. Any row returned indicates drift.
CREATE VIEW wallet_balance_drift AS
SELECT w.id                                   AS wallet_id,
       w.balance_minor                        AS cached_minor,
       COALESCE(SUM(p.signed_minor), 0)       AS ledger_minor
FROM wallet w
JOIN ledger_account a ON a.wallet_id = w.id
LEFT JOIN ledger_posting p ON p.account_id = a.id
GROUP BY w.id, w.balance_minor
HAVING w.balance_minor <> COALESCE(SUM(p.signed_minor), 0);
