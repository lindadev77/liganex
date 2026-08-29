-- 种子数据：权限字典 + 演示订单（ADR-0005 约定：建表后随迁移提供样例数据，便于本地联调与集成测试）
-- 幂等：重复执行不会产生重复数据

INSERT INTO permission (code, name, description) VALUES
    ('order:read',     '订单查询', '通过 MCP 或页面查询订单'),
    ('order:write',    '订单写入', '创建或修改订单（暂未开放）'),
    ('product:read',   '商品查询', '查询商品信息（暂未开放）'),
    ('inventory:read', '库存查询', '查询库存（暂未开放）')
ON CONFLICT (code) DO NOTHING;

-- 演示订单：覆盖多地区 + 多状态，落在 2026-08 分区
INSERT INTO customer_order (order_no, region, status, amount, currency, buyer_name, created_at) VALUES
    ('LNX-20260801-0001', 'US', 'PAID',      1289.00, 'USD', 'Alice Carter',    '2026-08-01 09:12:00+08'),
    ('LNX-20260801-0002', 'US', 'SHIPPED',    459.50, 'USD', 'Bob Nguyen',      '2026-08-01 14:30:00+08'),
    ('LNX-20260802-0003', 'EU', 'PENDING',    732.20, 'EUR', 'Clara Meyer',     '2026-08-02 10:05:00+08'),
    ('LNX-20260803-0004', 'EU', 'DELIVERED', 2150.00, 'EUR', 'Daniel Rossi',    '2026-08-03 16:44:00+08'),
    ('LNX-20260804-0005', 'JP', 'PAID',       980.00, 'JPY', '佐藤 健',          '2026-08-04 11:20:00+08'),
    ('LNX-20260805-0006', 'JP', 'CANCELLED',  320.75, 'JPY', '铃木 一郎',        '2026-08-05 09:00:00+08'),
    ('LNX-20260806-0007', 'US', 'DELIVERED',  1899.99, 'USD', 'Elena Petrova',  '2026-08-06 13:15:00+08'),
    ('LNX-20260807-0008', 'US', 'PENDING',     89.90, 'USD', 'Frank Zhao',      '2026-08-07 08:40:00+08'),
    ('LNX-20260808-0009', 'EU', 'SHIPPED',    1240.00, 'EUR', 'Grace Dubois',   '2026-08-08 15:55:00+08'),
    ('LNX-20260809-0010', 'US', 'PAID',       3499.00, 'USD', 'Henry Adams',    '2026-08-09 10:10:00+08'),
    ('LNX-20260810-0011', 'JP', 'SHIPPED',    1540.00, 'JPY', '田中 美咲',        '2026-08-10 12:00:00+08'),
    ('LNX-20260811-0012', 'EU', 'PAID',        675.30, 'EUR', 'Isabel Moreno',  '2026-08-11 17:25:00+08')
ON CONFLICT (order_no, created_at, region) DO NOTHING;
