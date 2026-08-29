-- 物流运单表（跨境 ERP 发货环节：order_ship 工具写入；ADR-0004 分区仅用于订单大表，运单为普通表）
CREATE TABLE IF NOT EXISTS shipment (
    id          BIGSERIAL PRIMARY KEY,
    order_no    VARCHAR(64)  NOT NULL,
    region      VARCHAR(16),
    carrier     VARCHAR(64)  NOT NULL,
    tracking_no VARCHAR(128) NOT NULL,
    shipped_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_shipment_order_no ON shipment (order_no);
CREATE INDEX  IF NOT EXISTS ix_shipment_tracking_no    ON shipment (tracking_no);

-- 权限字典：商品写 / 库存写（均有真实 MCP 工具承接，直接开放）
INSERT INTO permission (code, name, description, opened) VALUES
    ('product:write',   '商品维护', '通过 MCP 新建或更新商品',  TRUE),
    ('inventory:write', '库存调整', '通过 MCP 调整分仓库存',    TRUE)
ON CONFLICT (code) DO NOTHING;

-- 物流演示数据（挂在 V4 种子的已发货订单上）
INSERT INTO shipment (order_no, region, carrier, tracking_no, shipped_at) VALUES
    ('LNX-20260801-0002', 'US', 'UPS',   '1Z999AA10123456784', '2026-08-02 08:30:00+08'),
    ('LNX-20260808-0009', 'EU', 'DHL',   'JD014999003RR',      '2026-08-09 10:20:00+08'),
    ('LNX-20260810-0011', 'JP', '佐川急便', 'SGW-20260810-778', '2026-08-11 09:00:00+08')
ON CONFLICT (order_no) DO NOTHING;
