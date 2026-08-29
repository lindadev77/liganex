-- 用户与认证（ADR-0005：Flyway 版本化迁移，历史文件只读，结构变更须新增 V__ 文件）
-- 表名用 app_user 而非 user：user 是 Postgres 保留字。

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128),
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_email ON app_user (email);

COMMENT ON TABLE  app_user              IS 'B 端用户（注册/登录主体）';
COMMENT ON COLUMN app_user.password_hash IS 'BCrypt 哈希，禁止存明文';
COMMENT ON COLUMN app_user.status        IS 'ACTIVE | DISABLED';
