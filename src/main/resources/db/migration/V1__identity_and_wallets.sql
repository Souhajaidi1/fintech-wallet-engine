-- ---------------------------------------------------------------------------
-- V1: identity and wallets
--
-- Money is stored as BIGINT minor units (see ADR-002). No FLOAT, no DOUBLE.
-- ---------------------------------------------------------------------------

CREATE TABLE app_user (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(320) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    role           VARCHAR(16)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role  CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE wallet (
    id             UUID        PRIMARY KEY,
    owner_id       UUID        NOT NULL,
    label          VARCHAR(60) NOT NULL,
    currency       CHAR(3)     NOT NULL,
    balance_minor  BIGINT      NOT NULL DEFAULT 0,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_wallet_owner     FOREIGN KEY (owner_id) REFERENCES app_user (id),
    CONSTRAINT ck_wallet_status    CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    -- Last line of defence against overdraft. Even if every layer of application
    -- logic were bypassed, the database refuses to hold a negative balance.
    CONSTRAINT ck_wallet_non_negative CHECK (balance_minor >= 0),
    CONSTRAINT uq_wallet_owner_label  UNIQUE (owner_id, label)
);

CREATE INDEX ix_wallet_owner ON wallet (owner_id);
