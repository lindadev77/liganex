-- V5：开放平台 appsecret 改为可逆加密存储（ADR-0002 / ADR-0007）
--
-- 原 app_secret_hash 采用 BCrypt 单向哈希，无法用于 HMAC 验签。
-- MCP 调用方需要用 appsecret 作为 HMAC-SHA256 密钥，因此必须可还原。
-- 方案：以 AES-256-GCM 加密后存储密文（主密钥来自环境变量 LIGANEX_APP_SECRET_MASTER_KEY），
--       创建应用时一次性向调用方返回明文 appsecret，之后库内只有密文。

ALTER TABLE open_app RENAME COLUMN app_secret_hash TO app_secret_enc;

COMMENT ON COLUMN open_app.app_secret_enc IS
    'AES-256-GCM 加密的 appsecret（可逆，用于 MCP HMAC 验签）；主密钥来自环境变量 LIGANEX_APP_SECRET_MASTER_KEY';
