package tech.liganex.studio.module.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.common.PageResult;
import tech.liganex.studio.module.order.client.OrderQueryClient;
import tech.liganex.studio.module.order.dto.OrderDTO;
import tech.liganex.studio.module.order.dto.OrderQueryRequest;

import java.time.Instant;

/**
 * 前端订单查询接口（用户 JWT 鉴权，供 B 端页面使用）。
 *
 * <p>内部服务接口见 {@code /internal/v1/orders}（服务间凭证），两者共用 DTO 契约但鉴权体系不同。
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderQueryClient orderQueryClient;

    @GetMapping
    public ApiResponse<PageResult<OrderDTO>> query(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {

        return ApiResponse.ok(orderQueryClient.query(
                new OrderQueryRequest(region, status, from, to, page, size)));
    }
}
