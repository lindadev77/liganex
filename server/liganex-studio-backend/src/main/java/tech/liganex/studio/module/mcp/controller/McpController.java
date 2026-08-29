package tech.liganex.studio.module.mcp.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.mcp.security.McpAuthContext;
import tech.liganex.studio.module.mcp.security.McpAuthRequest;
import tech.liganex.studio.module.mcp.security.McpAuthService;
import tech.liganex.studio.module.mcp.service.AppCallLogService;
import tech.liganex.studio.module.order.client.OrderQueryClient;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;

import tools.jackson.databind.ObjectMapper;

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
 */
@RestController
@RequestMapping("/mcp/v1")
@Slf4j
@RequiredArgsConstructor
public class McpController {

    private static final String TOOL_ORDER_QUERY = "order_query";
    private static final String SCOPE_ORDER_READ = "order:read";

    private final McpAuthService authService;
    private final OrderQueryClient orderQueryClient;
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
        Map<String, Object> tool = Map.of(
                "name", TOOL_ORDER_QUERY,
                "description", "按地区/状态/时间查询跨境订单（需 order:read 权限）",
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
        return rpcResult(id, Map.of("tools", List.of(tool)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolsCall(Object id, Map<String, String> headers,
                                          Map<String, Object> request, String rawBody) {
        long start = System.currentTimeMillis();
        String appIdForAudit = header(headers, "x-app-id");

        try {
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            if (params == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "missing params");
            }
            String toolName = (String) params.get("name");
            if (!TOOL_ORDER_QUERY.equals(toolName)) {
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
                    SCOPE_ORDER_READ));

            OrderQueryRequest query = toOrderQuery(args);
            PageResult<OrderDTO> page = orderQueryClient.query(query);

            auditService.audit(ctx.appId(), TOOL_ORDER_QUERY, SCOPE_ORDER_READ,
                    "SUCCESS", System.currentTimeMillis() - start);

            String text = objectMapper.writeValueAsString(page);
            return rpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", text))));

        } catch (BizException ex) {
            auditService.audit(appIdForAudit, TOOL_ORDER_QUERY, SCOPE_ORDER_READ,
                    "FAIL:" + ex.errorCode().name(), System.currentTimeMillis() - start);
            return rpcError(id, -32000, ex.getMessage());
        } catch (Exception ex) {
            auditService.audit(appIdForAudit, TOOL_ORDER_QUERY, SCOPE_ORDER_READ,
                    "ERROR", System.currentTimeMillis() - start);
            log.error("mcp tools/call error", ex);
            return rpcError(id, -32603, "internal error");
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
