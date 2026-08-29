-- 开放平台：应用、权限字典、应用权限绑定、调用审计、配额（ADR-0002/0003）

CREATE TABLE IF NOT EXISTS open_app (
    id              BIGSERIAL    PRIMARY KEY,
    app_id          VARCHAR(64)  NOT NULL,
    app_secret_hash VARCHAR(255) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    owner_user_id   BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_open_app_app_id ON open_app (app_id);
CREATE INDEX IF NOT EXISTS ix_open_app_owner ON open_app (owner_user_id);

-- 权限字典：code 形如 order:read（{resource}:{action}）
CREATE TABLE IF NOT EXISTS permission (
    code        VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS app_permission (
    app_id          VARCHAR(64) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (app_id, permission_code)
);

-- 全量调用审计（ADR-0002）；result 记录成功/失败原因，不含任何凭证明文
CREATE TABLE IF NOT EXISTS app_call_log (
    id         BIGSERIAL   PRIMARY KEY,
    app_id     VARCHAR(64) NOT NULL,
    tool       VARCHAR(64),
    permission VARCHAR(64),
    result     VARCHAR(32) NOT NULL,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_app_call_log_app_time ON app_call_log (app_id, created_at DESC);

CREATE TABLE IF NOT EXISTS quota_usage (
    app_id     VARCHAR(64) NOT NULL,
    period     VARCHAR(16) NOT NULL,
    used       BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (app_id, period)
);

COMMENT ON TABLE  open_app             IS '开放平台应用；app_secret_hash 为 appsecret 的 BCrypt 哈希';
COMMENT ON COLUMN open_app.status      IS 'ACTIVE | DISABLED';
COMMENT ON TABLE  app_call_log         IS 'MCP 调用审计（ADR-0002），禁止记录 appsecret 明文';
