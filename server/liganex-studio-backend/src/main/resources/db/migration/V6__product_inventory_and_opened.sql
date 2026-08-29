-- 商品与库存目录（开放平台 MCP 工具的真实数据源：product:read / inventory:read）
-- product 为商品主数据；inventory 为分仓库存。均为小表，不做分区（ADR-0004 分区仅用于订单大表）。

CREATE TABLE IF NOT EXISTS product (
    id         BIGSERIAL PRIMARY KEY,
    sku        VARCHAR(64)   NOT NULL,
    name       VARCHAR(256)  NOT NULL,
    region     VARCHAR(16),
    price      NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency   VARCHAR(8)    NOT NULL DEFAULT 'USD',
    stock      INTEGER       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_sku   ON product (sku);
CREATE INDEX  IF NOT EXISTS ix_product_region      ON product (region);

CREATE TABLE IF NOT EXISTS inventory (
    id            BIGSERIAL PRIMARY KEY,
    sku           VARCHAR(64)   NOT NULL,
    region        VARCHAR(16),
    warehouse     VARCHAR(64)   NOT NULL,
    available_qty INTEGER       NOT NULL DEFAULT 0,
    locked_qty    INTEGER       NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_sku_region_wh
    ON inventory (sku, region, warehouse);

-- 权限字典：新增 opened 标记，前端只展示已开放（已落地真实接口）的权限项。
ALTER TABLE permission ADD COLUMN IF NOT EXISTS opened BOOLEAN NOT NULL DEFAULT TRUE;

-- 四个权限现均有真实 MCP 工具承接，全部开放；并修正描述（去掉"暂未开放"）。
UPDATE permission SET opened = TRUE, description = '通过 MCP 或页面写入订单'   WHERE code = 'order:write';
UPDATE permission SET opened = TRUE, description = '通过 MCP 查询商品信息'     WHERE code = 'product:read';
UPDATE permission SET opened = TRUE, description = '通过 MCP 查询分仓库存'     WHERE code = 'inventory:read';
UPDATE permission SET description = '通过 MCP 或页面查询订单'                  WHERE code = 'order:read';

-- 商品种子数据（演示）
INSERT INTO product (sku, name, region, price, currency, stock) VALUES
    ('SKU-1001', 'Wireless Earbuds',   'US', 29.99,  'USD', 1200),
    ('SKU-1002', 'Smart Watch',        'EU', 89.00,  'EUR', 540),
    ('SKU-1003', 'Bluetooth Speaker',  'JP', 4500.00,'JPY', 300),
    ('SKU-1004', 'Phone Case',         'US', 9.90,   'USD', 5000),
    ('SKU-1005', 'USB-C Cable',        'EU', 6.50,   'EUR', 8000)
ON CONFLICT (sku) DO NOTHING;

-- 库存种子数据（分仓）
INSERT INTO inventory (sku, region, warehouse, available_qty, locked_qty) VALUES
    ('SKU-1001', 'US', 'LA-01', 1000, 200),
    ('SKU-1002', 'EU', 'FRA-01', 500, 40),
    ('SKU-1003', 'JP', 'TYO-01', 280, 20),
    ('SKU-1004', 'US', 'LA-02', 4800, 200),
    ('SKU-1005', 'EU', 'FRA-02', 7800, 200)
ON CONFLICT (sku, region, warehouse) DO NOTHING;
