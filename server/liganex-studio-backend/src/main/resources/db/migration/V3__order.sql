-- 订单业务数据（跨境 ERP）
-- 分区策略：RANGE(created_at) 主分区 + LIST(region) 子分区（ADR-0004）
-- 分区键必须进主键：PK(id, created_at, region)
-- 新增月份的分区属于 schema 变更，须新增 V__ 迁移文件，不得回改本文件（ADR-0005）

CREATE TABLE IF NOT EXISTS customer_order (
    id         BIGSERIAL,
    order_no   VARCHAR(64)   NOT NULL,
    region     VARCHAR(16)   NOT NULL,
    status     VARCHAR(32)   NOT NULL,
    amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency   VARCHAR(8)    NOT NULL DEFAULT 'USD',
    buyer_name VARCHAR(128),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at, region)
) PARTITION BY RANGE (created_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_order_no_created_at_region
    ON customer_order (order_no, created_at, region);

-- 2026-08：按地区 LIST 子分区
CREATE TABLE IF NOT EXISTS customer_order_2026_08 PARTITION OF customer_order
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01')
    PARTITION BY LIST (region);
CREATE TABLE IF NOT EXISTS customer_order_2026_08_us PARTITION OF customer_order_2026_08 FOR VALUES IN ('US');
CREATE TABLE IF NOT EXISTS customer_order_2026_08_eu PARTITION OF customer_order_2026_08 FOR VALUES IN ('EU');
CREATE TABLE IF NOT EXISTS customer_order_2026_08_jp PARTITION OF customer_order_2026_08 FOR VALUES IN ('JP');
CREATE TABLE IF NOT EXISTS customer_order_2026_08_other PARTITION OF customer_order_2026_08 DEFAULT;

-- 2026-09：按地区 LIST 子分区
CREATE TABLE IF NOT EXISTS customer_order_2026_09 PARTITION OF customer_order
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01')
    PARTITION BY LIST (region);
CREATE TABLE IF NOT EXISTS customer_order_2026_09_us PARTITION OF customer_order_2026_09 FOR VALUES IN ('US');
CREATE TABLE IF NOT EXISTS customer_order_2026_09_eu PARTITION OF customer_order_2026_09 FOR VALUES IN ('EU');
CREATE TABLE IF NOT EXISTS customer_order_2026_09_jp PARTITION OF customer_order_2026_09 FOR VALUES IN ('JP');
CREATE TABLE IF NOT EXISTS customer_order_2026_09_other PARTITION OF customer_order_2026_09 DEFAULT;

-- 顶层兜底分区：避免无匹配范围的数据插入失败
CREATE TABLE IF NOT EXISTS customer_order_default PARTITION OF customer_order DEFAULT;

CREATE INDEX IF NOT EXISTS ix_order_region_status ON customer_order (region, status);
CREATE INDEX IF NOT EXISTS ix_order_created_at    ON customer_order (created_at DESC);

COMMENT ON TABLE  customer_order        IS '跨境订单（按月 RANGE + 按地区 LIST 分区）';
COMMENT ON COLUMN customer_order.region IS 'US | EU | JP | ...';
COMMENT ON COLUMN customer_order.status IS 'PENDING | PAID | SHIPPED | DELIVERED | CANCELLED';
