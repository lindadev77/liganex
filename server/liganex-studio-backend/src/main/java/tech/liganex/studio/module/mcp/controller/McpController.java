package tech.liganex.studio.module.mcp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.catalog.dto.InventoryDTO;
import tech.liganex.studio.module.catalog.dto.ProductDTO;
import tech.liganex.studio.module.catalog.entity.Inventory;
import tech.liganex.studio.module.catalog.entity.Product;
import tech.liganex.studio.module.catalog.mapper.InventoryMapper;
import tech.liganex.studio.module.catalog.mapper.ProductMapper;
import tech.liganex.studio.module.mcp.security.McpAuthContext;
import tech.liganex.studio.module.mcp.security.McpAuthRequest;
import tech.liganex.studio.module.mcp.security.McpAuthService;
import tech.liganex.studio.module.mcp.service.AppCallLogService;
import tech.liganex.studio.module.order.client.OrderQueryClient;
import tech.liganex.studio.module.order.client.OrderWriteClient;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;
import tech.liganex.studio.module.order.dto.OrderWriteRequest;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP server 入口（JSON-RPC 2.0 over HTTP，Streamable HTTP 风格的单一端点）。
 *
 * <p>鉴权：{@code initialize} / {@code tools/list} 为公开发现接口；{@code tools/call} 必须携带
 * 应用签名（X-App-Id / X-Timestamp / X-Nonce / X-Signature），由 {@link McpAuthService} 完成
 * HMAC 验签 + 防重放 + scope + 配额。签名覆盖「原始请求体」，因此控制器接收原始 body 字符串。
 *
 * <p>本端点不挂在 /api（用户 JWT）或 /internal（服务间 Key）之下，是第三套独立鉴权体系（ADR-0002/0009）。
 *
 * <p>当前开放的 MCP 工具（均对应权限字典中 opened=true 的真实接口）：
 * <ul>
 *   <li>{@code order_query}      — order:read      查询订单</li>
 *   <li>{@code order_write}      — order:write     创建订单</li>
 *   <li>{@code order_update}     — order:write     更新订单状态</li>
 *   <li>{@code order_ship}       — order:write     发货并登记物流运单</li>
 *   <li>{@code product_query}    — product:read    查询商品目录</li>
 *   <li>{@code product_write}    — product:write   新建/更新商品</li>
 *   <li>{@code inventory_query}  — inventory:read  查询分仓库存</li>
 *   <li>{@code inventory_adjust} — inventory:write 调整分仓库存</li>
 * </ul>
 */
@RestController
@RequestMapping("/mcp/v1")
@Slf4j
@RequiredArgsConstructor
public class McpController {

    private static final String TOOL_ORDER_QUERY = "order_query";
    private static final String TOOL_ORDER_WRITE = "order_write";
    private static final String TOOL_ORDER_UPDATE = "order_update";
    private static final String TOOL_ORDER_SHIP = "order_ship";
    private static final String TOOL_PRODUCT_QUERY = "product_query";
    private static final String TOOL_PRODUCT_WRITE = "product_write";
    private static final String TOOL_INVENTORY_QUERY = "inventory_query";
    private static final String TOOL_INVENTORY_ADJUST = "inventory_adjust";

    private static final String SCOPE_ORDER_READ = "order:read";
    private static final String SCOPE_ORDER_WRITE = "order:write";
    private static final String SCOPE_PRODUCT_READ = "product:read";
    private static final String SCOPE_PRODUCT_WRITE = "product:write";
    private static final String SCOPE_INVENTORY_READ = "inventory:read";
    private static final String SCOPE_INVENTORY_WRITE = "inventory:write";

    private final McpAuthService authService;
    private final OrderQueryClient orderQueryClient;
    private final OrderWriteClient orderWriteClient;
    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final AppCallLogService auditService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public Map<String, Object> handle(@RequestHeader Map<String, String> headers,
                                      @RequestBody String rawBody) {
        Object id;
        String method;
        Map<String, Object> request;
        try {
            request = objectMapper.readValue(rawBody, Map.class);
            id = request.get("id");
            method = (String) request.get("method");
        } catch (Exception ex) {
            return rpcError(null, -32700, "parse error");
        }
        if (method == null) {
            return rpcError(id, -32600, "invalid request: missing method");
        }
        return switch (method) {
            case "initialize" -> initialize(id);
            case "tools/list" -> toolsList(id);
            case "tools/call" -> toolsCall(id, headers, request, rawBody);
            default -> rpcError(id, -32601, "method not found: " + method);
        };
    }

    private Map<String, Object> initialize(Object id) {
        return rpcResult(id, Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "liganex-studio-mcp", "version", "0.1.0")));
    }

    private Map<String, Object> toolsList(Object id) {
        Map<String, Object> orderQuery = Map.of(
                "name", TOOL_ORDER_QUERY,
                "description", "按地区/状态/时间查询订单（需 order:read 权限）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "region", Map.of("type", "string", "description", "地区，如 US/EU/JP"),
                                "status", Map.of("type", "string", "description", "订单状态，如 PAID/SHIPPED"),
                                "from", Map.of("type", "string", "description", "起始时间 ISO-8601"),
                                "to", Map.of("type", "string", "description", "结束时间 ISO-8601"),
                                "page", Map.of("type", "integer", "description", "页码，默认 1"),
                                "size", Map.of("type", "integer", "description", "每页条数，默认 20")),
                        "required", List.of()));
        Map<String, Object> orderWrite = Map.of(
                "name", TOOL_ORDER_WRITE,
                "description", "创建一条订单（需 order:write 权限）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "region", Map.of("type", "string", "description", "地区，如 US/EU/JP，默认 US"),
                                "status", Map.of("type", "string", "description", "订单状态，默认 PENDING"),
                                "amount", Map.of("type", "number", "description", "订单金额，默认 0"),
                                "currency", Map.of("type", "string", "description", "币种，默认 USD"),
                                "buyerName", Map.of("type", "string", "description", "买家名称")),
                        "required", List.of()));
        Map<String, Object> productQuery = Map.of(
                "name", TOOL_PRODUCT_QUERY,
                "description", "按关键字/地区查询商品目录（需 product:read 权限）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of("type", "string", "description", "商品名或 SKU 模糊匹配"),
                                "region", Map.of("type", "string", "description", "地区过滤"),
                                "page", Map.of("type", "integer", "description", "页码，默认 1"),
                                "size", Map.of("type", "integer", "description", "每页条数，默认 20")),
                        "required", List.of()));
        Map<String, Object> inventoryQuery = Map.of(
                "name", TOOL_INVENTORY_QUERY,
                "description", "按 SKU/地区/仓库查询分仓库存（需 inventory:read 权限）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sku", Map.of("type", "string", "description", "商品 SKU"),
                                "region", Map.of("type", "string", "description", "地区"),
                                "warehouse", Map.of("type", "string", "description", "仓库编码")),
                        "required", List.of()));
        Map<String, Object> orderUpdate = Map.of(
                "name", TOOL_ORDER_UPDATE,
                "description", "更新订单状态（需 order:write 权限），如 PENDING→PAID→SHIPPED→DELIVERED / CANCELLED",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "orderNo", Map.of("type", "string", "description", "订单号"),
                                "status", Map.of("type", "string", "description", "目标状态，如 PAID/SHIPPED/DELIVERED/CANCELLED")),
                        "required", List.of("orderNo", "status")));
        Map<String, Object> orderShip = Map.of(
                "name", TOOL_ORDER_SHIP,
                "description", "发货：登记物流运单并把订单置为 SHIPPED（需 order:write 权限）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "orderNo", Map.of("type", "string", "description", "订单号"),
                                "carrier", Map.of("type", "string", "description", "承运商，如 UPS/DHL/佐川急便"),
                                "trackingNo", Map.of("type", "string", "description", "物流运单号")),
                        "required", List.of("orderNo", "carrier", "trackingNo")));
        Map<String, Object> productWrite = Map.of(
                "name", TOOL_PRODUCT_WRITE,
                "description", "按 SKU 新建或更新商品（需 product:write 权限）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sku", Map.of("type", "string", "description", "商品 SKU（唯一键）"),
                                "name", Map.of("type", "string", "description", "商品名称"),
                                "region", Map.of("type", "string", "description", "地区，如 US/EU/JP"),
                                "price", Map.of("type", "number", "description", "售价，默认 0"),
                                "currency", Map.of("type", "string", "description", "币种，默认 USD"),
                                "stock", Map.of("type", "integer", "description", "库存，默认 0")),
                        "required", List.of("sku", "name")));
        Map<String, Object> inventoryAdjust = Map.of(
                "name", TOOL_INVENTORY_ADJUST,
                "description", "调整指定仓库的可用库存（需 inventory:write 权限），delta 为增减量（可为负）",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "sku", Map.of("type", "string", "description", "商品 SKU"),
                                "region", Map.of("type", "string", "description", "地区"),
                                "warehouse", Map.of("type", "string", "description", "仓库编码"),
                                "delta", Map.of("type", "integer", "description", "库存增减量，正为入库、负为出库")),
                        "required", List.of("sku", "warehouse", "delta")));
        return rpcResult(id, Map.of("tools",
                List.of(orderQuery, orderWrite, orderUpdate, orderShip,
                        productQuery, productWrite, inventoryQuery, inventoryAdjust)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolsCall(Object id, Map<String, String> headers,
                                          Map<String, Object> request, String rawBody) {
        long start = System.currentTimeMillis();
        String appIdForAudit = header(headers, "x-app-id");
        String toolName = null;
        String scope = null;
        try {
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            if (params == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "missing params");
            }
            toolName = (String) params.get("name");
            scope = resolveScope(toolName);
            if (scope == null) {
                return rpcError(id, -32602, "unknown tool: " + toolName);
            }
            Map<String, Object> args = params.get("arguments") instanceof Map
                    ? (Map<String, Object>) params.get("arguments") : Map.of();

            McpAuthContext ctx = authService.verify(new McpAuthRequest(
                    header(headers, "x-app-id"),
                    "POST", "/mcp/v1",
                    header(headers, "x-timestamp"),
                    header(headers, "x-nonce"),
                    header(headers, "x-signature"),
                    rawBody,
                    scope));

            return switch (toolName) {
                case TOOL_ORDER_QUERY -> doOrderQuery(id, args, ctx, start);
                case TOOL_ORDER_WRITE -> doOrderWrite(id, args, ctx, start);
                case TOOL_ORDER_UPDATE -> doOrderUpdate(id, args, ctx, start);
                case TOOL_ORDER_SHIP -> doOrderShip(id, args, ctx, start);
                case TOOL_PRODUCT_QUERY -> doProductQuery(id, args, ctx, start);
                case TOOL_PRODUCT_WRITE -> doProductWrite(id, args, ctx, start);
                case TOOL_INVENTORY_QUERY -> doInventoryQuery(id, args, ctx, start);
                case TOOL_INVENTORY_ADJUST -> doInventoryAdjust(id, args, ctx, start);
                default -> rpcError(id, -32602, "unknown tool: " + toolName);
            };
        } catch (BizException ex) {
            auditService.audit(appIdForAudit, toolName == null ? "tools/call" : toolName,
                    scope == null ? "" : scope, "FAIL:" + ex.errorCode().name(),
                    System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        } catch (Exception ex) {
            auditService.audit(appIdForAudit, toolName == null ? "tools/call" : toolName,
                    scope == null ? "" : scope, "ERROR", System.currentTimeMillis() - start);
            log.error("mcp tools/call error", ex);
            return rpcError(id, -32603, "internal error");
        }
    }

    private String resolveScope(String toolName) {
        return switch (toolName) {
            case TOOL_ORDER_QUERY -> SCOPE_ORDER_READ;
            case TOOL_ORDER_WRITE, TOOL_ORDER_UPDATE, TOOL_ORDER_SHIP -> SCOPE_ORDER_WRITE;
            case TOOL_PRODUCT_QUERY -> SCOPE_PRODUCT_READ;
            case TOOL_PRODUCT_WRITE -> SCOPE_PRODUCT_WRITE;
            case TOOL_INVENTORY_QUERY -> SCOPE_INVENTORY_READ;
            case TOOL_INVENTORY_ADJUST -> SCOPE_INVENTORY_WRITE;
            default -> null;
        };
    }

    private Map<String, Object> doOrderQuery(Object id, Map<String, Object> args,
                                             McpAuthContext ctx, long start) {
        try {
            OrderQueryRequest query = toOrderQuery(args);
            PageResult<OrderDTO> page = orderQueryClient.query(query);
            auditService.audit(ctx.appId(), TOOL_ORDER_QUERY, SCOPE_ORDER_READ,
                    "SUCCESS", System.currentTimeMillis() - start);
            String text = objectMapper.writeValueAsString(page);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", text))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_ORDER_QUERY, SCOPE_ORDER_READ,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doOrderWrite(Object id, Map<String, Object> args,
                                             McpAuthContext ctx, long start) {
        try {
            String region = asString(args.get("region"));
            if (region == null) region = "US";
            String status = asString(args.get("status"));
            if (status == null) status = "PENDING";
            BigDecimal amount = parseDecimal(args.get("amount"));
            if (amount == null) amount = BigDecimal.ZERO;
            String currency = asString(args.get("currency"));
            if (currency == null) currency = "USD";
            String buyerName = asString(args.get("buyerName"));

            String orderNo = orderWriteClient.create(
                    new OrderWriteRequest(region, status, amount, currency, buyerName));

            Map<String, Object> result = Map.of(
                    "orderNo", orderNo,
                    "status", status,
                    "region", region,
                    "amount", amount,
                    "currency", currency,
                    "buyerName", buyerName == null ? "" : buyerName);
            auditService.audit(ctx.appId(), TOOL_ORDER_WRITE, SCOPE_ORDER_WRITE,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(result)))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_ORDER_WRITE, SCOPE_ORDER_WRITE,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doOrderUpdate(Object id, Map<String, Object> args,
                                              McpAuthContext ctx, long start) {
        try {
            String orderNo = asString(args.get("orderNo"));
            String status = asString(args.get("status"));
            if (orderNo == null || status == null) {
                return rpcError(id, -32602, "orderNo 与 status 均为必填");
            }
            OrderDTO order = orderWriteClient.updateStatus(orderNo, status);
            auditService.audit(ctx.appId(), TOOL_ORDER_UPDATE, SCOPE_ORDER_WRITE,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(order)))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_ORDER_UPDATE, SCOPE_ORDER_WRITE,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doOrderShip(Object id, Map<String, Object> args,
                                            McpAuthContext ctx, long start) {
        try {
            String orderNo = asString(args.get("orderNo"));
            String carrier = asString(args.get("carrier"));
            String trackingNo = asString(args.get("trackingNo"));
            if (orderNo == null || carrier == null || trackingNo == null) {
                return rpcError(id, -32602, "orderNo、carrier、trackingNo 均为必填");
            }
            OrderDTO order = orderWriteClient.ship(orderNo, carrier, trackingNo);
            auditService.audit(ctx.appId(), TOOL_ORDER_SHIP, SCOPE_ORDER_WRITE,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(order)))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_ORDER_SHIP, SCOPE_ORDER_WRITE,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doProductQuery(Object id, Map<String, Object> args,
                                               McpAuthContext ctx, long start) {
        try {
            String keyword = asString(args.get("keyword"));
            String region = asString(args.get("region"));
            long page = args.containsKey("page") && args.get("page") instanceof Number n ? n.longValue() : 1L;
            long size = args.containsKey("size") && args.get("size") instanceof Number n ? n.longValue() : 20L;

            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                    .and(StringUtils.hasText(keyword), w -> w
                            .like(Product::getName, keyword)
                            .or()
                            .like(Product::getSku, keyword))
                    .eq(StringUtils.hasText(region), Product::getRegion, region)
                    .orderByDesc(Product::getCreatedAt);
            var pg = productMapper.selectPage(new Page<>(page, size), wrapper);
            List<ProductDTO> records = pg.getRecords().stream().map(ProductDTO::from).toList();
            Map<String, Object> result = Map.of("total", pg.getTotal(), "items", records);

            auditService.audit(ctx.appId(), TOOL_PRODUCT_QUERY, SCOPE_PRODUCT_READ,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(result)))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_PRODUCT_QUERY, SCOPE_PRODUCT_READ,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doProductWrite(Object id, Map<String, Object> args,
                                               McpAuthContext ctx, long start) {
        try {
            String sku = asString(args.get("sku"));
            String name = asString(args.get("name"));
            if (sku == null || name == null) {
                return rpcError(id, -32602, "sku 与 name 均为必填");
            }
            String region = asString(args.get("region"));
            BigDecimal price = parseDecimal(args.get("price"));
            String currency = asString(args.get("currency"));
            Integer stock = parseInt(args.get("stock"));

            Product existing = productMapper.selectOne(
                    new LambdaQueryWrapper<Product>().eq(Product::getSku, sku));
            boolean updated = existing != null;
            Product product = updated ? existing : new Product();
            product.setSku(sku);
            product.setName(name);
            if (region != null) product.setRegion(region);
            if (price != null) product.setPrice(price);
            else if (!updated) product.setPrice(BigDecimal.ZERO);
            if (currency != null) product.setCurrency(currency);
            else if (!updated) product.setCurrency("USD");
            if (stock != null) product.setStock(stock);
            else if (!updated) product.setStock(0);

            if (updated) {
                productMapper.updateById(product);
            } else {
                product.setCreatedAt(Instant.now());
                productMapper.insert(product);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("updated", updated);
            result.put("product", ProductDTO.from(product));
            auditService.audit(ctx.appId(), TOOL_PRODUCT_WRITE, SCOPE_PRODUCT_WRITE,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(result)))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_PRODUCT_WRITE, SCOPE_PRODUCT_WRITE,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doInventoryQuery(Object id, Map<String, Object> args,
                                                 McpAuthContext ctx, long start) {
        try {
            String sku = asString(args.get("sku"));
            String region = asString(args.get("region"));
            String warehouse = asString(args.get("warehouse"));

            LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                    .eq(StringUtils.hasText(sku), Inventory::getSku, sku)
                    .eq(StringUtils.hasText(region), Inventory::getRegion, region)
                    .eq(StringUtils.hasText(warehouse), Inventory::getWarehouse, warehouse)
                    .orderByDesc(Inventory::getUpdatedAt);
            List<InventoryDTO> records = inventoryMapper.selectList(wrapper).stream()
                    .map(InventoryDTO::from).toList();
            Map<String, Object> result = Map.of("total", (long) records.size(), "items", records);

            auditService.audit(ctx.appId(), TOOL_INVENTORY_QUERY, SCOPE_INVENTORY_READ,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(result)))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_INVENTORY_QUERY, SCOPE_INVENTORY_READ,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    private Map<String, Object> doInventoryAdjust(Object id, Map<String, Object> args,
                                                  McpAuthContext ctx, long start) {
        try {
            String sku = asString(args.get("sku"));
            String warehouse = asString(args.get("warehouse"));
            Integer delta = parseInt(args.get("delta"));
            if (sku == null || warehouse == null || delta == null) {
                return rpcError(id, -32602, "sku、warehouse、delta 均为必填");
            }
            String region = asString(args.get("region"));

            Inventory inventory = inventoryMapper.selectOne(
                    new LambdaQueryWrapper<Inventory>()
                            .eq(Inventory::getSku, sku)
                            .eq(Inventory::getWarehouse, warehouse)
                            .eq(StringUtils.hasText(region), Inventory::getRegion, region));
            if (inventory == null) {
                return rpcError(id, -32000, "未找到对应的分仓库存记录: " + sku + " @ " + warehouse);
            }
            inventory.setAvailableQty(inventory.getAvailableQty() + delta);
            inventory.setUpdatedAt(Instant.now());
            inventoryMapper.updateById(inventory);

            auditService.audit(ctx.appId(), TOOL_INVENTORY_ADJUST, SCOPE_INVENTORY_WRITE,
                    "SUCCESS", System.currentTimeMillis() - start);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text",
                            objectMapper.writeValueAsString(InventoryDTO.from(inventory))))));
        } catch (Exception ex) {
            auditService.audit(ctx.appId(), TOOL_INVENTORY_ADJUST, SCOPE_INVENTORY_WRITE,
                    "ERROR", System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private OrderQueryRequest toOrderQuery(Map<String, Object> args) {
        String region = asString(args.get("region"));
        String status = asString(args.get("status"));
        Instant from = parseInstant(args.get("from"));
        Instant to = parseInstant(args.get("to"));
        long page = args.containsKey("page") && args.get("page") instanceof Number n ? n.longValue() : 1L;
        long size = args.containsKey("size") && args.get("size") instanceof Number n ? n.longValue() : 20L;
        return new OrderQueryRequest(region, status, from, to, page, size);
    }

    private String asString(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private Instant parseInstant(Object o) {
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s);
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(Object o) {
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s);
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    private Integer parseInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (RuntimeException ex) {
                return null;
            }
        }
        return null;
    }

    private String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> rpcResult(Object id, Object result) {
        Map<String, Object> m = new HashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("result", result);
        return m;
    }

    private Map<String, Object> rpcError(Object id, int code, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("jsonrpc", "2.0");
        m.put("id", id);
        m.put("error", Map.of("code", code, "message", message));
        return m;
    }
}
